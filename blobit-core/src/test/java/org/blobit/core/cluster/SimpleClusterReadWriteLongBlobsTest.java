/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package org.blobit.core.cluster;

import static org.junit.Assert.assertTrue;
import herddb.jdbc.HerdDBEmbeddedDataSource;
import herddb.server.ServerConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import org.blobit.core.api.BucketConfiguration;
import org.blobit.core.api.BucketHandle;
import org.blobit.core.api.Configuration;
import org.blobit.core.api.ObjectManager;
import org.blobit.core.api.ObjectManagerFactory;
import org.blobit.core.api.PutPromise;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SimpleClusterReadWriteLongBlobsTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

//    @Before
    public void setupLogger() throws Exception {
        Level level = Level.FINER;
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                System.err.println("uncaughtException from thread " + t.
                        getName() + ": " + e);
                e.printStackTrace();
            }
        });
        java.util.logging.LogManager.getLogManager().reset();
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(level);
        SimpleFormatter f = new SimpleFormatter();
        ch.setFormatter(f);
        java.util.logging.Logger.getLogger("").setLevel(level);
        java.util.logging.Logger.getLogger("").addHandler(ch);
    }

    private static final String BUCKET_ID = "mybucket";
    private static final byte[] TEST_DATA = new byte[4 * 1024 * 1024];

    static {
        Random random = new Random();
        random.nextBytes(TEST_DATA);
    }

    @Test
    public void testWrite() throws Exception {
        Properties dsProperties = new Properties();
        dsProperties.put(ServerConfiguration.PROPERTY_MODE,
                ServerConfiguration.PROPERTY_MODE_LOCAL);
        try (ZKTestEnv env = new ZKTestEnv(tmp.newFolder("zk").toPath());
                HerdDBEmbeddedDataSource datasource =
                new HerdDBEmbeddedDataSource(
                        dsProperties)) {
            env.startBookie();
            Configuration configuration =
                    new Configuration()
                            .setType(Configuration.TYPE_BOOKKEEPER)
                            .setConcurrentWriters(10)
                            .setZookeeperUrl(env.getAddress());

            try (ObjectManager manager = ObjectManagerFactory.
                    createObjectManager(configuration, datasource);) {
                long _start = System.currentTimeMillis();
                Collection<PutPromise> batch = new LinkedBlockingQueue<>();

                manager.createBucket(BUCKET_ID, BUCKET_ID,
                        BucketConfiguration.DEFAULT).get();
                BucketHandle bucket = manager.getBucket(BUCKET_ID);
                ExecutorService exec = Executors.newFixedThreadPool(4);

                for (int i = 0; i < 100; i++) {

                    exec.submit(new Runnable() {
                        @Override
                        public void run() {
                            batch.add(bucket.put(null, TEST_DATA));
                        }
                    });
                }
                exec.shutdown();
                assertTrue(exec.awaitTermination(1, TimeUnit.MINUTES));

                List<String> ids = new ArrayList<>();
                for (PutPromise f : batch) {
                    ids.add(f.get());
                }

                for (String id : ids) {
//                    System.out.println("waiting for id " + id);
                    Assert.assertArrayEquals(TEST_DATA, bucket.get(id).get());
                }

                long _stop = System.currentTimeMillis();
                double speed =
                        (int) (batch.size() * 60_000.0 / (_stop - _start));
                double band = speed * TEST_DATA.length;
                long total =
                        (batch.size() * TEST_DATA.length * 1L) / (1024 * 1024);
                System.out.println(
                        "TIME: " + (_stop - _start) + " ms for " + batch.size() + " blobs, total " + total + " MBs, "
                        + speed + " blobs/h, " + speed * 24 + " blobs/day " + (band / 1e9) + " Gbytes/h");
            }
        }
    }
}
