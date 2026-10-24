package com.snowgears.shop.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogHandlerMariaDbVersionTest {

    @Test
    void mariadbDataSourceClassIsLoadable() throws Exception {
        Class<?> dataSourceClass = Class.forName("org.mariadb.jdbc.MariaDbDataSource");
        assertNotNull(dataSourceClass);
    }
}
