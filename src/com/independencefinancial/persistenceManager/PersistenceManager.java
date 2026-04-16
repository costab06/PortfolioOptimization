package com.bcfinancial.persistenceManager;

import java.sql.SQLException;


public interface PersistenceManager {
    Persistable select (String tableName, Object key) throws Exception;
    void insert (String tableName, Object key, Persistable persistable) throws SQLException;
    void update (String tableName, Object key, Persistable persistable) throws SQLException;
    void delete (String tableName, Object key) throws SQLException;
}
    
