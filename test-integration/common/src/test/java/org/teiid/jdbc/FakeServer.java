/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.teiid.jdbc;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.resource.spi.XATerminator;
import javax.transaction.TransactionManager;

import org.teiid.adminapi.VDB;
import org.teiid.adminapi.impl.ModelMetaData;
import org.teiid.adminapi.impl.VDBImportMetadata;
import org.teiid.adminapi.impl.VDBMetaData;
import org.teiid.common.queue.FakeWorkManager;
import org.teiid.core.util.SimpleMock;
import org.teiid.core.util.UnitTestUtil;
import org.teiid.deployers.UDFMetaData;
import org.teiid.deployers.VirtualDatabaseException;
import org.teiid.dqp.internal.datamgr.ConnectorManager;
import org.teiid.dqp.internal.datamgr.ConnectorManagerRepository;
import org.teiid.dqp.internal.process.DQPCore;
import org.teiid.dqp.service.BufferService;
import org.teiid.dqp.service.FakeBufferService;
import org.teiid.metadata.FunctionMethod;
import org.teiid.metadata.MetadataRepository;
import org.teiid.metadata.MetadataStore;
import org.teiid.metadata.Schema;
import org.teiid.metadata.index.IndexMetadataStore;
import org.teiid.metadata.index.VDBMetadataFactory;
import org.teiid.query.optimizer.capabilities.BasicSourceCapabilities;
import org.teiid.query.optimizer.capabilities.SourceCapabilities;
import org.teiid.runtime.EmbeddedConfiguration;
import org.teiid.runtime.EmbeddedServer;
import org.teiid.transport.ClientServiceRegistryImpl;

@SuppressWarnings({"nls"})
public class FakeServer extends EmbeddedServer {
	
	public static class DeployVDBParameter {
		public Map<String, Collection<FunctionMethod>> udfs;
		public MetadataRepository<?, ?> metadataRepo;
		public List<VDBImportMetadata> vdbImports;

		public DeployVDBParameter(Map<String, Collection<FunctionMethod>> udfs,
				MetadataRepository<?, ?> metadataRepo) {
			this.udfs = udfs;
			this.metadataRepo = metadataRepo;
		}
	}
	
	private boolean realBufferManager;
	
	@SuppressWarnings("serial")
	public FakeServer(boolean start) {
		cmr = new ProviderAwareConnectorManagerRepository() {
			@Override
			public ConnectorManager getConnectorManager(String connectorName) {
        		ConnectorManager cm = super.getConnectorManager(connectorName);
        		if (cm != null) {
        			return cm;
        		}
        		if (connectorName.equalsIgnoreCase("source")) {
        			return new ConnectorManager("x", "x") {
        	        	@Override
        	        	public SourceCapabilities getCapabilities() {
        	        		return new BasicSourceCapabilities();
        	        	}
        			};
        		}
        		return null;
			}
		};
		if (start) {
			start(new EmbeddedConfiguration(), false);
		}
	}

	public void start(EmbeddedConfiguration config, boolean realBufferMangaer) {
		if (config.getSystemStore() == null) {
			config.setSystemStore(VDBMetadataFactory.getSystem());
		}
		if (config.getTransactionManager() == null) {
			config.setTransactionManager(SimpleMock.createSimpleMock(TransactionManager.class));
			this.transactionService.setXaTerminator(SimpleMock.createSimpleMock(XATerminator.class));
			this.transactionService.setWorkManager(new FakeWorkManager());
			detectTransactions = false;
		}
		this.repo.odbcEnabled();
		this.realBufferManager = realBufferMangaer;
		start(config);
	}
	
	@Override
	protected BufferService getBufferService() {
		if (!realBufferManager) {
			return new FakeBufferService(false);
		}
		bufferService.setDiskDirectory(UnitTestUtil.getTestScratchPath());
		return super.getBufferService();
	}
	
	public DQPCore getDqp() {
		return dqp;
	}
	
	public ConnectorManagerRepository getConnectorManagerRepository() {
		return cmr;
	}
	
	public void setConnectorManagerRepository(ConnectorManagerRepository cmr) {
		this.cmr = cmr;
	}
	
	public void setUseCallingThread(boolean useCallingThread) {
		this.useCallingThread = useCallingThread;
	}
	
	public void deployVDB(String vdbName, String vdbPath) throws Exception {
        deployVDB(vdbName, vdbPath, new DeployVDBParameter(null, null));		
	}	

	public void deployVDB(String vdbName, String vdbPath, DeployVDBParameter parameterObject) throws Exception {
		IndexMetadataStore imf = VDBMetadataFactory.loadMetadata(vdbName, new File(vdbPath).toURI().toURL());
        deployVDB(vdbName, imf, parameterObject);		
	}
	
	public void deployVDB(String vdbName, MetadataStore metadata) {
		deployVDB(vdbName, metadata, new DeployVDBParameter(null, null));
	}

	public void deployVDB(String vdbName, MetadataStore metadata, DeployVDBParameter parameterObject) {
		VDBMetaData vdbMetaData = new VDBMetaData();
        vdbMetaData.setName(vdbName);
        vdbMetaData.setStatus(VDB.Status.ACTIVE);
        
        for (Schema schema : metadata.getSchemas().values()) {
        	ModelMetaData model = addModel(vdbMetaData, schema);
        	if (parameterObject.metadataRepo != null) {
        		model.addAttchment(MetadataRepository.class, parameterObject.metadataRepo);
        	}
        }
                        
        try {
        	UDFMetaData udfMetaData = null;
        	if (parameterObject.udfs != null) {
        		udfMetaData = new UDFMetaData();
        		for (Map.Entry<String, Collection<FunctionMethod>> entry : parameterObject.udfs.entrySet()) {
        			udfMetaData.addFunctions(entry.getKey(), entry.getValue());
        		}
        	}
        	
        	if (parameterObject.vdbImports != null) {
        		for (VDBImportMetadata vdbImport : parameterObject.vdbImports) {
					vdbMetaData.getVDBImports().add(vdbImport);
				}
        	}
        	
			this.repo.addVDB(vdbMetaData, metadata, (metadata instanceof IndexMetadataStore)?((IndexMetadataStore)metadata).getEntriesPlusVisibilities():null, udfMetaData, cmr);
			this.repo.finishDeployment(vdbMetaData.getName(), vdbMetaData.getVersion());
			this.repo.getVDB(vdbMetaData.getName(), vdbMetaData.getVersion()).setStatus(VDB.Status.ACTIVE);
		} catch (VirtualDatabaseException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void removeVDB(String vdbName) {
		this.repo.removeVDB(vdbName, 1);
	}

	private ModelMetaData addModel(VDBMetaData vdbMetaData, Schema schema) {
		ModelMetaData model = new ModelMetaData();
		model.setName(schema.getName());
		vdbMetaData.addModel(model);
		model.addSourceMapping("source", "translator", "jndi:source");
		return model;
	}
	
	public VDBMetaData getVDB(String vdbName) {
		return this.repo.getVDB(vdbName, 1);
	}
	
	public void undeployVDB(String vdbName) {
		this.repo.removeVDB(vdbName, 1);
	}
	
	public ConnectionImpl createConnection(String embeddedURL) throws Exception {
		return getDriver().connect(embeddedURL, null);
	}
	
	public ClientServiceRegistryImpl getClientServiceRegistry() {
		return services;
	}
	
}
