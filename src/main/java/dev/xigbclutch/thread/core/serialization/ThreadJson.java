package dev.xigbclutch.thread.core.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Creates consistently configured Gson instances for Thread core serialization. */
public final class ThreadJson {
  private ThreadJson() {}

  /** Creates a serializer that retains contract fields with null values. */
  public static Gson create() {
    return new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
  }
}
