package org.gradoop.demo.server;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.gradoop.demo.server.FetchStatus.Status.FETCHED_FROM_HDFS;
import static org.gradoop.demo.server.FetchStatus.Status.FETCH_ERROR;
import static org.gradoop.demo.server.FetchStatus.Status.PRESENT_LOCALLY;

class LocalGradoopGraphsetStore implements GradoopGraphsetStore {

    private static final Logger log = LoggerFactory.getLogger(LocalGradoopGraphsetStore.class);
    static final String DEFAULT_LOCAL_PATH = System.getProperty("user.dir") + "/target/classes/data";
    private final File localBase;
    private final HdfsGradoopGraphsetStore hdfsStore;

    LocalGradoopGraphsetStore(Configuration config, String localPath) {
        this(config, HdfsGradoopGraphsetStore.DEFAULT_BASE_PATH_IN_HDFS, localPath);
    }

    LocalGradoopGraphsetStore(Configuration config, String remoteBasePath, String localPath) {
        requireNonNull(remoteBasePath);
        requireNonNull(localPath);
        localBase = Paths.get(localPath).toFile();
        if (!localBase.isDirectory()) {
            throw new IllegalArgumentException("not a directory: " + localBase);
        }
        if (config != null) {
            hdfsStore = new HdfsGradoopGraphsetStore(config, remoteBasePath);
        } else {
            hdfsStore = null; // we are configured to run off local
        }
    }

    @Override
    public Set<String> getDataSourceNames() {
        // we are sure here that localBase is a directory!
        return stream(localBase.listFiles(File::isDirectory)).map(File::getName).collect(toSet());
    }

    @Override
    public String getPath(String dataSourceName) {
        return localBase.getAbsolutePath() + "/" + dataSourceName;
    }

    @Override
    public Set<FetchStatus> refresh() throws IOException {
        if (hdfsStore == null) {
            return emptySet();
        }
        Set<String> localNames = this.getDataSourceNames();
        Set<String> remoteNames = hdfsStore.getDataSourceNames();
        Set<FetchStatus> result = new HashSet<>(remoteNames.size() + localNames.size());
        for (String remoteName : remoteNames) {
            FetchStatus fetched = fetch(remoteName);
            if (fetched.getStatus() == FETCHED_FROM_HDFS) {
                boolean a = result.add(fetched);
                assert a;
                boolean b = localNames.remove(remoteName);
                if (!b) {
                    log.debug("graphset was not available locally: ", remoteName);
                }
                log.info("**********************************************");
                log.info("Graphset was copied from HDFS: {}", remoteName);
                log.info("**********************************************");
            }
        }
        // remaining
        localNames.forEach(name -> {
            FetchStatus e = new FetchStatus(name, PRESENT_LOCALLY);
            log.info("**********************************************");
            log.info("Graphset was present locally: {}", name);
            log.info("**********************************************");
            result.add(e);
        });
        return result;
    }

    @Override
    public boolean isLocal() {
        return this.hdfsStore == null;
    }

    /**
     * Fetch the required files (metadata.csv, vertices.csv, edges.csv) for the given Gradoop graphset name
     * the files are copied to {@linkplain #localBase}. The structure would be:
     */
    private FetchStatus fetch(String graphsetName) {
        try {
            hdfsStore.copyGradoopFiles(graphsetName, localBase);
            return new FetchStatus(graphsetName, FETCHED_FROM_HDFS);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            log.warn("{} could not be fetched from HDFS", graphsetName);
            return new FetchStatus(graphsetName, FETCH_ERROR);
        }
    }

    @Override
    public String toString() {
        String hdfs = this.hdfsStore == null ? null : this.hdfsStore.toString();
        return "**** [HDFS Store: " + hdfs + ", Local Base Path: " +
            this.localBase.getAbsolutePath() + "] ****";
    }
}
