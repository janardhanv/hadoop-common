/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.namenode.snapshot;

import java.io.IOException;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.UnresolvedLinkException;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockManager;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.FSNamesystem;
import org.apache.hadoop.hdfs.server.namenode.LeaseManager;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.ipc.ProtobufRpcEngine.Server;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.log4j.Level;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Testing nested snapshots. */
public class TestNestedSnapshots {
  {
    ((Log4JLogger)DataNode.LOG).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)LeaseManager.LOG).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)LogFactory.getLog(BlockManager.class)).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)LogFactory.getLog(FSNamesystem.class)).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)NameNode.stateChangeLog).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)NameNode.blockStateChangeLog).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)DFSClient.LOG).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)Server.LOG).getLogger().setLevel(Level.OFF);
    ((Log4JLogger)LogFactory.getLog(UserGroupInformation.class)).getLogger().setLevel(Level.OFF);
  }

  private static final long SEED = 0;
  private static final short REPLICATION = 3;
  private static final long BLOCKSIZE = 1024;
  
  private static Configuration conf = new Configuration();
  private static MiniDFSCluster cluster;
  private static FSNamesystem fsn;
  private static DistributedFileSystem hdfs;
  
  @BeforeClass
  public static void setUp() throws Exception {
    cluster = new MiniDFSCluster.Builder(conf).numDataNodes(REPLICATION)
        .build();
    cluster.waitActive();

    fsn = cluster.getNamesystem();
    hdfs = cluster.getFileSystem();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (cluster != null) {
      cluster.shutdown();
    }
  }
  
  /**
   * Create a snapshot for /test/foo and create another snapshot for
   * /test/foo/bar.  Files created before the snapshots should appear in both
   * snapshots and the files created after the snapshots should not appear in
   * any of the snapshots.  
   */
  @Test
  public void testNestedSnapshots() throws Exception {
    final Path foo = new Path("/test/foo");
    final Path bar = new Path(foo, "bar");
    final Path file1 = new Path(bar, "file1");
    DFSTestUtil.createFile(hdfs, file1, BLOCKSIZE, REPLICATION, SEED);
    print("create file " + file1);

    final String s1name = "foo-s1";
    final Path s1path = SnapshotTestHelper.getSnapshotRoot(foo, s1name); 
    hdfs.allowSnapshot(foo.toString());
    print("allow snapshot " + foo);
    hdfs.createSnapshot(s1name, foo.toString());
    print("create snapshot " + s1name);

    final String s2name = "bar-s2";
    final Path s2path = SnapshotTestHelper.getSnapshotRoot(bar, s2name); 
    hdfs.allowSnapshot(bar.toString());
    print("allow snapshot " + bar);
    hdfs.createSnapshot(s2name, bar.toString());
    print("create snapshot " + s2name);

    final Path file2 = new Path(bar, "file2");
    DFSTestUtil.createFile(hdfs, file2, BLOCKSIZE, REPLICATION, SEED);
    print("create file " + file2);
    
    assertFile(s1path, s2path, file1, true, true, true);
    assertFile(s1path, s2path, file2, true, false, false);
  }

  private static void print(String mess) throws UnresolvedLinkException {
    System.out.println("XXX " + mess);
    SnapshotTestHelper.dumpTreeRecursively(fsn.getFSDirectory().getINode("/"));
  }

  private static void assertFile(Path s1, Path s2, Path file,
      Boolean... expected) throws IOException {
    final Path[] paths = {
        file,
        new Path(s1, "bar/" + file.getName()),
        new Path(s2, file.getName())
    };
    Assert.assertEquals(expected.length, paths.length);
    for(int i = 0; i < paths.length; i++) {
      final boolean computed = hdfs.exists(paths[i]);
      Assert.assertEquals("Failed on " + paths[i], expected[i], computed);
    }
  }
}
