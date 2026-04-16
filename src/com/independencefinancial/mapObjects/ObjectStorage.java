package com.bcfinancial.mapObjects;


import java.io.*;

public interface ObjectStorage {
  public void put (Object key, StorageFields object) throws IOException;
  public RetrievalFields get (Object key) throws IOException;
}
