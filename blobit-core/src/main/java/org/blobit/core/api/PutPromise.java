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
package org.blobit.core.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.bookkeeper.common.concurrent.FutureUtils;

/**
 * Result of a Put, it reports immediately the ID of the object
 *
 * @author enrico.olivelli
 */
public final class PutPromise {

    public final String id;
    public final CompletableFuture<?> future;

    public PutPromise(String id, CompletableFuture<?> future) {
        this.id = id;
        this.future = future;
    }

    public String get(long timeout, TimeUnit t) throws InterruptedException, ObjectManagerException, TimeoutException {
        try {
            FutureUtils.result(future, timeout, t);
            return id;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (TimeoutException | ObjectManagerException ie) {
            throw ie;
        } catch (Exception err) {
            throw new ObjectManagerException(err);
        }
    }

    public String get() throws InterruptedException, ObjectManagerException {
        try {
            FutureUtils.result(future);
            return id;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (ObjectManagerException ie) {
            throw ie;
        } catch (Exception err) {
            throw new ObjectManagerException(err);
        }
    }

}
