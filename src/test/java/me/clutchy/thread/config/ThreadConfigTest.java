package me.clutchy.thread.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThreadConfigTest {
  @Test
  void defaultsEnableTheFutureLocalTransport() {
    assertTrue(ThreadConfig.defaults().mcpEnabled());
  }
}
