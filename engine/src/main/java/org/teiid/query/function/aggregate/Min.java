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

package org.teiid.query.function.aggregate;

import java.util.List;

import org.teiid.api.exception.query.ExpressionEvaluationException;
import org.teiid.api.exception.query.FunctionExecutionException;
import org.teiid.core.TeiidComponentException;
import org.teiid.query.QueryPlugin;
import org.teiid.query.util.ErrorMessageKeys;


/**
 */
public class Min extends AggregateFunction {

    private Object minValue;

    public void reset() {
        minValue = null;
    }

    /**
     * @see org.teiid.query.function.aggregate.AggregateFunction#addInputDirect(Object, List)
     */
    public void addInputDirect(Object value, List<?> tuple)
        throws FunctionExecutionException, ExpressionEvaluationException, TeiidComponentException {

        if(minValue == null) {
            minValue = value;
        } else {
            if(value instanceof Comparable) {
                Comparable valueComp = (Comparable) value;

                if(valueComp.compareTo(minValue) < 0) {
                    minValue = valueComp;
                }
            } else {
                throw new FunctionExecutionException(ErrorMessageKeys.FUNCTION_0050, QueryPlugin.Util.getString(ErrorMessageKeys.FUNCTION_0050, "MIN", value.getClass().getName())); //$NON-NLS-1$
            }
        }
    }

    /**
     * @see org.teiid.query.function.aggregate.AggregateFunction#getResult()
     */
    public Object getResult() {
        return this.minValue;
    }


}
