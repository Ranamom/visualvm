/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.visualvm.heapviewer.utils;

import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.SwingUtilities;
import org.graalvm.visualvm.heapviewer.HeapFragment;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapProgress;
import org.graalvm.visualvm.lib.jfluid.heap.Instance;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;

/**
 *
 * @author Jiri Sedlacek
 */
@NbBundle.Messages({
    "HeapOperations_ComputingReferences=Computing References...",
    "HeapOperations_ComputingGCRoots=Computing GC Roots..."
})
public final class HeapOperations {
    
    private static Map<Heap, HeapOperations> INSTANCES;
    
    
    private HeapOperations() {}
    
    
    private static synchronized HeapOperations get(Heap heap) {
        if (INSTANCES == null) INSTANCES = new WeakHashMap();
        
        HeapOperations instance = INSTANCES.get(heap);
        if (instance == null) {
            instance = new HeapOperations();
            INSTANCES.put(heap, instance);
        }
        
        return instance;
    }
    
    
    public static void initializeReferences(Heap heap) throws InterruptedException {
        get(heap).initializeReferencesImpl(heap);
    }
    
    public static void initializeGCRoots(Heap heap) throws InterruptedException {
        get(heap).initializeGCRootsImpl(heap);
    }
    
    
    // --- References ----------------------------------------------------------
    
    private static boolean referencesInitialized;
    private static volatile Thread referencesComputer;
    
    private void initializeReferencesImpl(Heap heap) throws InterruptedException {
        Thread _referencesComputer;
        
        synchronized (this) {
            if (referencesInitialized) return;
            
            if (referencesComputer == null) {
                Runnable workerR = new Runnable() {
                    public void run() {
                        ProgressHandle pHandle = null;

                        try {
                            pHandle = ProgressHandle.createHandle(Bundle.HeapOperations_ComputingReferences());
                            pHandle.setInitialDelay(1000);
                            pHandle.start(HeapProgress.PROGRESS_MAX);

                            HeapFragment.setProgress(pHandle, 0);

                            Instance dummy = (Instance)heap.getAllInstancesIterator().next();
                            dummy.getReferences();
                        } finally {
                            if (pHandle != null) pHandle.finish();
                        }

                        synchronized (this) {
                            referencesInitialized = false;
                            referencesComputer = null;
                        }
                    }
                };
                referencesComputer = new Thread(workerR, "References Computer"); // NO18N
                _referencesComputer = referencesComputer; // NOTE: must be assigned before starting the thread which eventually nulls the referencesComputer!
                referencesComputer.start();
            } else {
                _referencesComputer = referencesComputer;
            }
        }
        
        assert !SwingUtilities.isEventDispatchThread();

        _referencesComputer.join();
    }
    
    
    // --- GC Roots ------------------------------------------------------------
    
    private static boolean gcrootsInitialized;
    private static volatile Thread gcrootsComputer;
    
    private void initializeGCRootsImpl(Heap heap) throws InterruptedException {
        initializeReferencesImpl(heap);
        
        Thread _gcrootsComputer;
        
        synchronized (this) {
            if (gcrootsInitialized) return;
            
            if (gcrootsComputer == null) {
                Runnable workerR = new Runnable() {
                    public void run() {
                        ProgressHandle pHandle = null;

                        try {
                            pHandle = ProgressHandle.createHandle(Bundle.HeapOperations_ComputingGCRoots());
                            pHandle.setInitialDelay(1000);
                            pHandle.start(HeapProgress.PROGRESS_MAX);

                            HeapFragment.setProgress(pHandle, 0);

                            Instance dummy = (Instance)heap.getAllInstancesIterator().next();
                            dummy.getNearestGCRootPointer();
                        } finally {
                            if (pHandle != null) pHandle.finish();
                        }

                        synchronized (this) {
                            gcrootsInitialized = false;
                            gcrootsComputer = null;
                        }
                    }
                };
                gcrootsComputer = new Thread(workerR, "GC Roots Computer"); // NO18N
                _gcrootsComputer = gcrootsComputer; // NOTE: must be assigned before starting the thread which eventually nulls the gcrootsComputer!
                gcrootsComputer.start();
            } else {
                _gcrootsComputer = gcrootsComputer;
            }
        }
        
        assert !SwingUtilities.isEventDispatchThread();

        _gcrootsComputer.join();
    }
    
}