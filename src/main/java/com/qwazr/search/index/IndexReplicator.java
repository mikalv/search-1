/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.index;

import com.qwazr.search.analysis.AnalyzerDefinition;
import com.qwazr.search.field.FieldDefinition;
import com.qwazr.server.ServerException;
import com.qwazr.utils.IOUtils;
import org.apache.lucene.replicator.IndexAndTaxonomyReplicationHandler;
import org.apache.lucene.replicator.IndexReplicationHandler;
import org.apache.lucene.replicator.PerSessionDirectoryFactory;
import org.apache.lucene.replicator.ReplicationClient;
import org.apache.lucene.replicator.Replicator;
import org.apache.lucene.replicator.Revision;
import org.apache.lucene.replicator.SessionToken;
import org.apache.lucene.store.Directory;

import javax.ws.rs.core.Response;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

class IndexReplicator implements Replicator {

	private final IndexServiceInterface indexService;
	private final RemoteIndex master;
	private final File masterUuidFile;
	private final ReplicationClient replicationClient;

	private volatile String masterUuidString;
	private volatile UUID masterUuid;
	private volatile ReplicationStatus.Builder currentStatus;

	IndexReplicator(final IndexServiceInterface localService, final RemoteIndex master, final File masterUuidFile,
			final Directory indexDirectory, final Directory taxonomyDirectory, final Path replWorkPath,
			final Callable<Boolean> callback) throws URISyntaxException, IOException {
		replicationClient =
				new ReplicationClient(this, getNewReplicationHandler(indexDirectory, taxonomyDirectory, callback),
						new PerSessionDirectoryFactory(replWorkPath));
		this.master = master;
		this.masterUuidFile = masterUuidFile;
		this.indexService = master == null ? null : master.host == null ? localService : new IndexSingleClient(master);
		if (masterUuidFile.exists() && masterUuidFile.length() > 0) {
			this.masterUuid = UUID.fromString(IOUtils.readFileAsString(masterUuidFile));
			this.masterUuidString = masterUuid.toString();
		} else
			checkRemoteMasterUuid();
	}

	IndexServiceInterface checkService() {
		if (indexService == null)
			throw new ServerException(Response.Status.NOT_ACCEPTABLE, "The remote master has not been set");
		return indexService;
	}

	final String checkRemoteMasterUuid() throws IOException {
		final UUID remoteMasterUuid;
		try {
			remoteMasterUuid = UUID.fromString(checkService().getIndex(master.schema, master.index).index_uuid);
		} catch (Exception e) {
			throw ServerException.of(
					"Wrong status from replication master " + master.schema + '/' + master.index + " " + e.getMessage(),
					e);
		}
		if (masterUuid == null) {
			masterUuid = remoteMasterUuid;
			masterUuidString = masterUuid.toString();
			IOUtils.writeStringAsFile(masterUuidString, masterUuidFile);
			return masterUuidString;
		}
		if (!Objects.equals(remoteMasterUuid, masterUuid))
			throw new ServerException(Response.Status.NOT_ACCEPTABLE,
					"The local master index UUID and the remote index UUID does not match: " + masterUuid + " <> " +
							remoteMasterUuid);
		return masterUuidString;
	}

	final UUID getMasterUuid() {
		return masterUuid;
	}

	final LinkedHashMap<String, AnalyzerDefinition> getMasterAnalyzers() {
		return checkService().getAnalyzers(master.schema, master.index);
	}

	final LinkedHashMap<String, FieldDefinition> getMasterFields() {
		return checkService().getFields(master.schema, master.index);
	}

	final LinkedHashMap<String, IndexInstance.ResourceInfo> getMasterResources() {
		return checkService().getResources(master.schema, master.index);
	}

	final IndexStatus getMasterStatus() {
		return checkService().getIndex(master.schema, master.index);
	}

	final InputStream getResource(final String resourceName) throws IOException {
		return checkService().getResource(master.schema, master.index, resourceName);
	}

	interface Slave {
		IndexReplicator getIndexReplicator();
	}

	@Override
	final public void release(final String sessionID) throws IOException {
		checkService().replicationRelease(master.schema, master.index, masterUuidString, sessionID);
	}

	@Override
	final public InputStream obtainFile(final String sessionID, final String source, final String fileName)
			throws IOException {
		if (currentStatus != null)
			currentStatus.countSize(source, fileName);
		return checkService().replicationObtain(master.schema, master.index, masterUuidString, sessionID, source,
				fileName);
	}

	@Override
	final public void close() {
		replicationClient.close();
	}

	@Override
	final public void publish(final Revision revision) throws IOException {
		throw new UnsupportedOperationException(
				"this replicator implementation does not support remote publishing of revisions");
	}

	@Override
	public SessionToken checkForUpdate(final String currVersion) throws IOException {
		try (final InputStream inputStream = checkService().replicationUpdate(master.schema, master.index,
				masterUuidString, currVersion)) {
			if (inputStream == null)
				return null;
			if (inputStream.available() == 0)
				return null;
			final DataInput input = new DataInputStream(inputStream);
			final SessionToken sessionToken = new SessionToken(input);
			if (currentStatus != null)
				currentStatus.sessionToken(sessionToken);
			return sessionToken;
		}
	}

	final void updateNow(ReplicationStatus.Builder currentStatus) throws IOException {
		this.currentStatus = currentStatus;
		replicationClient.updateNow();
	}

	static ReplicationClient.ReplicationHandler getNewReplicationHandler(final Directory dataDirectory,
			final Directory taxoDirectory, final Callable<Boolean> callback) throws IOException {
		return taxoDirectory == null ?
				new IndexReplicationHandler(dataDirectory, callback) :
				new IndexAndTaxonomyReplicationHandler(dataDirectory, taxoDirectory, callback);
	}

}
