/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.ha;

import static org.neo4j.helpers.collection.MapUtil.stringMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.cluster.ClusterSettings;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.ha.HaSettings;
import org.neo4j.kernel.ha.HighlyAvailableGraphDatabase;
import org.neo4j.kernel.impl.nioneo.xa.NeoStoreXaDataSource;
import org.neo4j.kernel.impl.transaction.XaDataSourceManager;
import org.neo4j.test.LoggerRule;
import org.neo4j.test.ha.ClusterManager;

/**
 * This is a test for the Neo4j HA self-inflicted DDOS "pull storm" phenomenon. In a 2 instance setup, whereby
 * the slave has been down for awhile thus causing it to be substantially behind on transactions, when it comes back online
 * and the master tries to push to it, if many transactions concurrently commit, causing concurrent pullUpdate calls,
 * this may cause a DDOS on itself, due to too many concurrent transaction synchronizations, causing timeouts.
 *
 */
public class PullStormIT
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder(  );

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Test
    public void testPullStorm() throws Throwable
    {
        ClusterManager clusterManager = new ClusterManager( ClusterManager.clusterWithAdditionalArbiters( 2, 1 ),
                folder.getRoot(),
                stringMap( HaSettings.pull_interval.name(), "0",
                           HaSettings.tx_push_factor.name(), "1") );

        clusterManager.start();

        try
        {
            ClusterManager.ManagedCluster cluster = clusterManager.getDefaultCluster();
            cluster.await( ClusterManager.masterAvailable(  ) );
            cluster.await( ClusterManager.masterSeesSlavesAsAvailable( 1 ) );

            // Create data
            final HighlyAvailableGraphDatabase master = cluster.getMaster();
            {
                System.out.println( "Creating data" );
                Transaction tx = master.beginTx();
                for ( int i = 0; i < 1000; i++ )
                {
                    master.createNode().setProperty( "foo", "bar" );
                }
                tx.success();
                tx.finish();
            }

            // Slave goes down
            HighlyAvailableGraphDatabase slave = cluster.getAnySlave();
            System.out.println( "Slave failed" );
            ClusterManager.RepairKit repairKit = cluster.fail( slave );

            // Create more data
            System.out.println( "Creating more data" );
            for ( int i = 0; i < 1000; i++ )
            {
                {
                    Transaction tx = master.beginTx();
                    for ( int j = 0; j < 1000; j++ )
                    {
                        master.createNode().setProperty( "foo", "bar" );
                        master.createNode().setProperty( "foo", "bar" );
                    }
                    tx.success();
                    tx.finish();
                }
            }

            // Slave comes back online
            System.out.println( "Slave comes up" );
            repairKit.repair();

            cluster.await( ClusterManager.masterSeesSlavesAsAvailable( 1 ) );

            // Create 20 concurrent transactions
            System.out.println( "Pull storm" );
            ExecutorService executor = Executors.newFixedThreadPool( 20 );
            List<Future<?>> result = new ArrayList<Future<?>>(  );
            for ( int i = 0; i < 20; i++ )
            {
                result.add( executor.submit( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Transaction tx = master.beginTx();
                        master.createNode().setProperty( "foo", "bar" );
                        tx.success();
                        tx.finish(); // This should cause lots of concurrent calls to pullUpdate()
                    }
                } ) );
            }

            for ( Future<?> future : result )
            {
                future.get();
            }

            executor.shutdown();

            System.out.println( "Pull storm done" );

            for ( HighlyAvailableGraphDatabase highlyAvailableGraphDatabase : cluster.getAllMembers() )
            {
                long txId = ((NeoStoreXaDataSource)highlyAvailableGraphDatabase.getDependencyResolver().resolveDependency( XaDataSourceManager.class ).getXaDataSource( NeoStoreXaDataSource.DEFAULT_DATA_SOURCE_NAME )).getNeoStore().getLastCommittedTx();
                System.out.println(highlyAvailableGraphDatabase.getConfig().get( ClusterSettings.server_id )+"="+txId);
            }
        }
        finally
        {
            System.err.println( "Shutting down" );
            clusterManager.shutdown();
            System.err.println( "Shut down" );
        }
    }
}
