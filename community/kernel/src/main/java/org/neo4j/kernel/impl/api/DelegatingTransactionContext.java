/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api;

import org.neo4j.kernel.api.StatementContext;
import org.neo4j.kernel.api.TransactionContext;
import org.neo4j.kernel.api.exceptions.TransactionFailureException;

public class DelegatingTransactionContext implements TransactionContext
{
    protected final TransactionContext delegate;

    public DelegatingTransactionContext( TransactionContext delegate )
    {
        this.delegate = delegate;
    }

    @Override
    public StatementContext newStatementContext()
    {
        return delegate.newStatementContext();
    }

    @Override
    public void prepare()
    {
        delegate.prepare();
    }

    @Override
    public void commit() throws TransactionFailureException
    {
        delegate.commit();
    }

    @Override
    public void rollback() throws TransactionFailureException
    {
        delegate.rollback();
    }
}
