package me.clutchy.thread.core.provider;

import me.clutchy.thread.core.model.options.ClientOptionsQuery;
import me.clutchy.thread.core.model.options.ClientOptionsSnapshot;
import me.clutchy.thread.core.tool.ToolResult;

/** Supplies detached local client settings without requiring a world or gameplay session. */
public interface ClientOptionsProvider {
  /** Reads only the selected option sections without mutating the running client. */
  ToolResult<ClientOptionsSnapshot> options(ClientOptionsQuery query);
}
