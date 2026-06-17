package com.kovanlabs.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

@SpringBootTest
class DbQueryTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void inspectOrganizationId() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, null, "organization_id");
            System.out.println("=== TABLES WITH organization_id ===");
            while (columns.next()) {
                String tableName = columns.getString("TABLE_NAME");
                System.out.println("Table: " + tableName);
            }
            System.out.println("==================================");

            // Let's try querying a few tables to see if we can find any organization_id values
            try (Statement stmt = conn.createStatement()) {
                // Let's query one of the tables, e.g., app_user or alert or others if they have it
                ResultSet rs = stmt.executeQuery("SELECT DISTINCT organization_id FROM alert WHERE organization_id IS NOT NULL");
                System.out.println("=== Alert organization_ids ===");
                while (rs.next()) {
                    System.out.println("Org ID in alert: " + rs.getString(1));
                }
            } catch (Exception e) {
                System.out.println("Error querying alert organization_id: " + e.getMessage());
            }

            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='public'");
                System.out.println("=== ALL TABLES ===");
                while (rs.next()) {
                    System.out.println("Table: " + rs.getString(1));
                }
            } catch (Exception e) {
                System.out.println("Error listing tables: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Error inspecting database: " + e.getMessage());
        }
    }
}


