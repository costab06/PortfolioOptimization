package com.bcfinancial.persistenceManager;

import java.io.Serializable;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class Persistable implements Serializable {
    public static String getKeyFieldName() {return null;}
    public static String getCreateTableString(String tableName) {return null;}
    public static void init(String tableName) {}

}
