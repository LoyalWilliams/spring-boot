/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.data.cassandra;

import java.net.InetSocketAddress;
import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.city.City;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.config.SessionFactoryFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CassandraDataAutoConfiguration} that require a Cassandra instance.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 */
@Testcontainers(disabledWithoutDocker = true)
class CassandraDataAutoConfigurationIntegrationTests {

	@Container
	static final CassandraContainer<?> cassandra = new CassandraContainer<>().withStartupAttempts(5)
			.withStartupTimeout(Duration.ofMinutes(10));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(CassandraAutoConfiguration.class, CassandraDataAutoConfiguration.class))
			.withPropertyValues("spring.data.cassandra.contact-points:localhost:" + cassandra.getFirstMappedPort(),
					"spring.data.cassandra.local-datacenter=datacenter1", "spring.data.cassandra.read-timeout=20s",
					"spring.data.cassandra.connect-timeout=10s")
			.withInitializer((context) -> AutoConfigurationPackages.register((BeanDefinitionRegistry) context,
					City.class.getPackage().getName()));

	@Test
	void hasDefaultSchemaActionSet() {
		this.contextRunner.run((context) -> assertThat(context.getBean(SessionFactoryFactoryBean.class))
				.hasFieldOrPropertyWithValue("schemaAction", SchemaAction.NONE));
	}

	@Test
	void hasRecreateSchemaActionSet() {
		createTestKeyspaceIfNotExists();
		this.contextRunner
				.withPropertyValues("spring.data.cassandra.schemaAction=recreate_drop_unused",
						"spring.data.cassandra.keyspaceName=boot_test")
				.run((context) -> assertThat(context.getBean(SessionFactoryFactoryBean.class))
						.hasFieldOrPropertyWithValue("schemaAction", SchemaAction.RECREATE_DROP_UNUSED));
	}

	private void createTestKeyspaceIfNotExists() {
		try (CqlSession session = CqlSession.builder()
				.withConfigLoader(DriverConfigLoader.programmaticBuilder()
						.withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(10))
						.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(20)).build())
				.addContactPoint(
						new InetSocketAddress(cassandra.getContainerIpAddress(), cassandra.getFirstMappedPort()))
				.withLocalDatacenter("datacenter1").build()) {
			session.execute("CREATE KEYSPACE IF NOT EXISTS boot_test"
					+ "  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };");
		}
	}

}
