/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze;

import io.crate.exceptions.RepositoryAlreadyExistsException;
import io.crate.exceptions.RepositoryUnknownException;
import io.crate.test.integration.CrateDummyClusterServiceUnitTest;
import io.crate.testing.SQLExecutor;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.RepositoriesMetaData;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ClusterServiceUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static io.crate.testing.SettingMatcher.hasEntry;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

public class CreateDropRepositoryAnalyzerTest extends CrateDummyClusterServiceUnitTest {

    private SQLExecutor e;

    @Before
    public void prepare() {
        RepositoriesMetaData repositoriesMetaData = new RepositoriesMetaData(
            Collections.singletonList(
                new RepositoryMetaData(
                    "my_repo",
                    "fs",
                    Settings.builder().put("location", "/tmp/my_repo").build()
            )));
        ClusterState clusterState = ClusterState.builder(new ClusterName("testing"))
            .metaData(MetaData.builder()
                .putCustom(RepositoriesMetaData.TYPE, repositoriesMetaData))
            .build();
        ClusterServiceUtils.setState(clusterService, clusterState);
        e = SQLExecutor.builder(clusterService).build();
    }

    @Test
    public void testCreateRepository() {
        CreateRepositoryAnalyzedStatement statement = e.analyze(
            "CREATE REPOSITORY \"new_repository\" TYPE \"fs\" with (location='/mount/backups/my_backup', compress=True)");
        assertThat(statement.repositoryName(), is("new_repository"));
        assertThat(statement.repositoryType(), is("fs"));
        assertThat(statement.settings().get("compress"), is("true"));
        assertThat(statement.settings().get("location"), is("/mount/backups/my_backup"));
    }

    @Test
    public void testCreateExistingRepository() {
        expectedException.expect(RepositoryAlreadyExistsException.class);
        expectedException.expectMessage("Repository 'my_repo' already exists");
        e.analyze("create repository my_repo TYPE fs");
    }

    @Test
    public void testDropUnknownRepository() {
        expectedException.expect(RepositoryUnknownException.class);
        expectedException.expectMessage("Repository 'unknown_repo' unknown");
        e.analyze("DROP REPOSITORY \"unknown_repo\"");
    }

    @Test
    public void testDropExistingRepo() {
        DropRepositoryAnalyzedStatement statement = e.analyze("DROP REPOSITORY my_repo");
        assertThat(statement.repositoryName(), is("my_repo"));
    }

    @Test
    public void testCreateS3RepositoryWithAllSettings() {
        CreateRepositoryAnalyzedStatement analysis = e.analyze(
            "CREATE REPOSITORY foo TYPE s3 WITH (" +
            "   bucket='abc'," +
            "   endpoint='www.example.com'," +
            "   protocol='http'," +
            "   base_path='/holz/'," +
            "   access_key='0xAFFE'," +
            "   secret_key='0xCAFEE'," +
            "   chunk_size='12mb'," +
            "   compress=true," +
            "   server_side_encryption=false," +
            "   buffer_size='5mb'," +
            "   max_retries=2," +
            "   use_throttle_retries=false," +
            "   canned_acl=false)");
        assertThat(analysis.repositoryType(), is("s3"));
        assertThat(analysis.repositoryName(), is("foo"));
        assertThat(
            analysis.settings(),
            allOf(
                hasEntry("access_key", "0xAFFE"),
                hasEntry("base_path", "/holz/"),
                hasEntry("bucket", "abc"),
                hasEntry("buffer_size", "5mb"),
                hasEntry("canned_acl", "false"),
                hasEntry("chunk_size", "12mb"),
                hasEntry("compress", "true"),
                hasEntry("endpoint", "www.example.com"),
                hasEntry("max_retries", "2"),
                hasEntry("use_throttle_retries", "false"),
                hasEntry("protocol", "http"),
                hasEntry("secret_key", "0xCAFEE"),
                hasEntry("server_side_encryption", "false"))
        );
    }

    @Test
    public void testCreateS3RepoWithMissingMandatorySettings() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The following required parameters are missing to" +
                                        " create a repository of type \"s3\": [secret_key]");
        e.analyze("CREATE REPOSITORY foo TYPE s3 WITH (access_key='test')");
    }

    @Test
    public void testCreateAzureRepositoryWithAllSettings() {
        CreateRepositoryAnalyzedStatement analysis = e.analyze(
            "CREATE REPOSITORY foo TYPE azure WITH (" +
            "   container='test_container'," +
            "   base_path='test_path'," +
            "   chunk_size='12mb'," +
            "   compress=true," +
            "   readonly=true," +
            "   location_mode='primary_only'," +
            "   account='test_account'," +
            "   key='test_key'," +
            "   max_retries=3," +
            "   endpoint_suffix='.com'," +
            "   timeout='30s'," +
            "   proxy_port=0," +
            "   proxy_type='socks'," +
            "   proxy_host='localhost')");
        assertThat(analysis.repositoryType(), is("azure"));
        assertThat(analysis.repositoryName(), is("foo"));
        assertThat(
            analysis.settings(),
            allOf(
                hasEntry("container", "test_container"),
                hasEntry("base_path", "test_path"),
                hasEntry("chunk_size", "12mb"),
                hasEntry("compress", "true"),
                hasEntry("readonly", "true"),
                hasEntry("account", "test_account"),
                hasEntry("key", "test_key"),
                hasEntry("max_retries", "3"),
                hasEntry("endpoint_suffix", ".com"),
                hasEntry("timeout", "30s"),
                hasEntry("proxy_port", "0"),
                hasEntry("proxy_type", "SOCKS"),
                hasEntry("proxy_host", "localhost")
            )
        );
    }

    @Test
    public void testCreateAzureRepoWithMissingMandatorySettings() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The following required parameters are missing to" +
                                        " create a repository of type \"azure\": [key]");
        e.analyze("CREATE REPOSITORY foo TYPE azure WITH (account='test')");
    }

    @Test
    public void testCreateAzureRepoWithWrongSettings() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("setting 'wrong' not supported");
        e.analyze("CREATE REPOSITORY foo TYPE azure WITH (wrong=true)");
    }

    @Test
    public void testCreateS3RepoWithWrongSettings() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("setting 'wrong' not supported");
        e.analyze("CREATE REPOSITORY foo TYPE s3 WITH (wrong=true)");
    }
}
