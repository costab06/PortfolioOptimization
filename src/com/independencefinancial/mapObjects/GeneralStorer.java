package com.bcfinancial.mapObjects;


import java.io.*;
import java.util.*;

public abstract class GeneralStorer implements ObjectStorer {
  private ObjectStorage storage;
  
  protected GeneralStorer (ObjectStorage storage) {
    this.storage = storage;
  }

  public void put (Object key, Object object) throws IOException {
    if (object == null) {
      storage.put (key, null);
    } else {
      storage.put (key, getFields (object));
    }
  }

  protected abstract StorageFields getFields (Object object) throws IOException;
  
  public Object get (Object key) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    RetrievalFields fields = storage.get (key);
    if (fields == null) {
      return null;
    } else {
      return setFields (fields);
    }
  }

  protected abstract Object setFields (RetrievalFields object) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException;
}
