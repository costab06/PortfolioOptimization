package com.bcfinancial.mapObjects;


import java.util.*;

public class MapStorage implements ObjectStorage {
  private static String classNameKey = "@class";
  
  private Map storageMap;
  
  public MapStorage (Map storageMap) {
    this.storageMap = storageMap;
  }

  public void put (Object key, StorageFields object) {
    Map objectMap = new HashMap ();
    String className = object.getClassName ();
    objectMap.put (classNameKey, className);
    Iterator fields = object.getFieldNames ();
    while (fields.hasNext ()) {
      Object field = fields.next ();
      Object value = object.getValue (field);
      if (value != null)
        objectMap.put (field, value);
      else
        objectMap.put (field, objectMap);
    }
    storageMap.put (key, objectMap);
  }

  public void remove (Object key) {
    storageMap.remove (key);
  }
  
  public RetrievalFields get (Object key) {
    Map objectMap = (Map) storageMap.get (key);
    if (objectMap == null)
      return null;
    String className = (String) objectMap.get (classNameKey);
    RetrievalFields object = new RetrievalFields (className);
    Iterator fields = objectMap.keySet ().iterator ();
    while (fields.hasNext ()) {
      String field = (String) fields.next ();
      Object value = objectMap.get (field);
      if (value == objectMap)
        value = null;
      object.addField (field, value);
    }
    return object;
  }
}
