package com.bcfinancial.mapObjects;

import java.util.*;

public class StorageFields {
  private String className;
  private List fieldNames;
  private Map fieldTypes, fieldValues;

  public StorageFields (String className) {
    this.className = className;
    fieldNames = new ArrayList ();
    fieldTypes = new HashMap ();
    fieldValues = new HashMap ();
  }

  public void addField (String field, Class type, Object value) {
    if (fieldTypes.containsKey (field))
      throw new IllegalArgumentException ("Duplicate field: " + field);
    fieldNames.add (field);
    fieldTypes.put (field, type);
    if (value != null)
      fieldValues.put (field, value);
  }

  public String getClassName () {
    return className;
  }

  public Iterator getFieldNames () {
    return fieldNames.iterator ();
  }

  public Class getType (Object field) {
    return (Class) fieldTypes.get (field);
  }

  public Object getValue (Object field) {
    return fieldValues.get (field);
  }

  public String toString () {
    StringBuffer result = new StringBuffer ("StorageFields[");
    result.append ("className=").append (className);
    result.append (",fields={");
    Iterator fields = getFieldNames ();
    while (fields.hasNext ()) {
      Object field = fields.next ();
      result.append (field);
      result.append ('(').append (getType (field)).append (")=");
      result.append (getValue (field));
      if (fields.hasNext ())
        result.append (',');
    }
    result.append ("}]");
    return result.toString ();
  }
}
