package me.clutchy.thread.platform.fabric.recipe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.game.FabricProviderSupport;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import me.clutchy.thread.platform.fabric.threading.MinecraftThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

/**
 * Reads item identities and the final live recipe set from the running Fabric game.
 *
 * <p>The integrated server's recipe manager already reflects vanilla data plus active datapack and
 * mod resource additions, replacements, and removals after reload. Thread does not maintain a
 * parallel static vanilla recipe catalog.
 */
public final class FabricRecipeProvider implements RecipeProvider {
  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final FabricSessionGuard sessionGuard;
  private final FabricProviderLimits limits;
  private final FabricDtoMapper mapper;
  private final Duration gameThreadTimeout;

  public FabricRecipeProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      FabricSessionGuard sessionGuard,
      FabricProviderLimits limits,
      FabricDtoMapper mapper,
      Duration gameThreadTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.gameThreadTimeout = Objects.requireNonNull(gameThreadTimeout, "gameThreadTimeout");
  }

  @Override
  public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
    ToolResult<RecipeReadContext> contextResult =
        FabricProviderSupport.read(
            clientThread, "get_recipe.session", () -> captureRecipeContext(itemId));
    if (!contextResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(contextResult.error()));
    }

    RecipeReadContext context = Objects.requireNonNull(contextResult.value());
    // RecipeManager belongs to the integrated server. Leaving the client task before waiting on
    // the server avoids holding one game thread while another performs the bounded snapshot.
    GameThreadExecutor serverThread =
        MinecraftThreadExecutor.forServer(context.server(), gameThreadTimeout);
    return FabricProviderSupport.read(
        serverThread, "get_recipe.recipes", () -> readRecipes(context));
  }

  @Override
  public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
    return FabricProviderSupport.read(
        clientThread, "search_items", () -> searchItemsOnClient(query, limit));
  }

  private ToolResult<RecipeReadContext> captureRecipeContext(String itemId) {
    if (itemId == null || itemId.isBlank()) {
      return failure(ToolErrorCode.INVALID_INPUT, "itemId must not be blank.", false);
    }
    Identifier identifier = Identifier.tryParse(itemId);
    if (identifier == null) {
      return failure(ToolErrorCode.INVALID_INPUT, "itemId must be a valid registry ID.", false);
    }
    if (BuiltInRegistries.ITEM.getOptional(identifier).isEmpty()) {
      return failure(ToolErrorCode.NOT_FOUND, "The requested item is not registered.", false);
    }

    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }

    MinecraftServer server = Objects.requireNonNull(client.getSingleplayerServer());
    ResourceKey<Level> dimension = Objects.requireNonNull(client.level).dimension();
    return ToolResult.success(new RecipeReadContext(server, dimension, identifier));
  }

  private ToolResult<List<RecipeInfo>> readRecipes(RecipeReadContext context) {
    ServerLevel level = context.server().getLevel(context.dimension());
    if (level == null) {
      return failure(
          ToolErrorCode.WORLD_NOT_AVAILABLE,
          "The current server world is no longer available.",
          true);
    }

    // Read the manager's resolved collection on the server thread. This is the same live snapshot
    // gameplay uses after vanilla, datapack, and mod-provided recipe resources have been composed.
    Collection<RecipeHolder<?>> definitions = context.server().getRecipeManager().getRecipes();
    if (definitions.size() > limits.maxRecipeDefinitions()) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              "The live recipe registry exceeds Thread's safety limit.",
              false,
              Map.of(
                  "actual", Integer.toString(definitions.size()),
                  "limit", Integer.toString(limits.maxRecipeDefinitions()))));
    }

    ContextMap displayContext = SlotDisplayContext.fromLevel(level);
    List<RecipeHolder<?>> orderedDefinitions =
        definitions.stream()
            .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
            .toList();
    List<RecipeInfo> matches = new ArrayList<>();
    for (RecipeHolder<?> holder : orderedDefinitions) {
      Optional<ItemStack> result = resultFor(holder.value(), context.targetId(), displayContext);
      if (result.isEmpty()) {
        continue;
      }
      if (matches.size() >= limits.maxRecipesPerItem()) {
        return ToolResult.failure(
            new ToolError(
                ToolErrorCode.RESULT_LIMIT_EXCEEDED,
                "The requested item has more recipes than Thread can return safely.",
                false,
                Map.of("limit", Integer.toString(limits.maxRecipesPerItem()))));
      }
      matches.add(toRecipeInfo(holder, result.orElseThrow()));
    }
    return ToolResult.success(List.copyOf(matches));
  }

  private ToolResult<ItemSearchResult> searchItemsOnClient(String query, int limit) {
    if (query == null || query.isBlank()) {
      return failure(ToolErrorCode.INVALID_INPUT, "query must not be blank.", false);
    }
    if (limit <= 0) {
      return failure(ToolErrorCode.INVALID_INPUT, "limit must be positive.", false);
    }
    if (limit > limits.maxItemSearchResults()) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              "The requested item result limit exceeds Thread's safety limit.",
              false,
              Map.of(
                  "requested", Integer.toString(limit),
                  "limit", Integer.toString(limits.maxItemSearchResults()))));
    }
    if (BuiltInRegistries.ITEM.size() > limits.maxItemDefinitions()) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              "The live item registry exceeds Thread's safety limit.",
              false,
              Map.of(
                  "actual", Integer.toString(BuiltInRegistries.ITEM.size()),
                  "limit", Integer.toString(limits.maxItemDefinitions()))));
    }

    return ToolResult.success(searchRegistry(query, limit));
  }

  static ItemSearchResult searchRegistry(String query, int limit) {
    String normalizedQuery = query.strip();
    String[] terms = normalizedQuery.toLowerCase(Locale.ROOT).split("\\s+");
    List<ItemInfo> matches = new ArrayList<>();
    var definitions =
        BuiltInRegistries.ITEM.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
            .toList();
    for (var entry : definitions) {
      String canonicalId = entry.getKey().identifier().toString();
      String displayName = Component.translatable(entry.getValue().getDescriptionId()).getString();
      if (matches(terms, canonicalId, displayName)) {
        matches.add(new ItemInfo(canonicalId, displayName));
        if (matches.size() > limit) {
          break;
        }
      }
    }
    boolean truncated = matches.size() > limit;
    List<ItemInfo> returned = truncated ? matches.subList(0, limit) : matches;
    return new ItemSearchResult(normalizedQuery, limit, truncated, returned);
  }

  private RecipeInfo toRecipeInfo(RecipeHolder<?> holder, ItemStack result) {
    String recipeType = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString();
    return new RecipeInfo(
        holder.id().identifier().toString(),
        recipeType,
        mapper.itemStack(result),
        ingredientGroups(holder.value()));
  }

  @SuppressWarnings("deprecation")
  private static List<RecipeIngredientInfo> ingredientGroups(Recipe<?> recipe) {
    Map<IngredientAlternatives, Integer> occurrences = new LinkedHashMap<>();
    for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
      TreeSet<String> itemIds = new TreeSet<>();
      ingredient
          .items()
          .map(holder -> holder.unwrapKey().map(ResourceKey::identifier))
          .flatMap(Optional::stream)
          .map(Identifier::toString)
          .forEach(itemIds::add);

      TreeSet<String> tagIds = new TreeSet<>();
      collectTagIds(ingredient.display(), tagIds);
      IngredientAlternatives alternatives =
          new IngredientAlternatives(List.copyOf(itemIds), List.copyOf(tagIds));
      if (!alternatives.itemIds().isEmpty() || !alternatives.tagIds().isEmpty()) {
        occurrences.merge(alternatives, 1, Integer::sum);
      }
    }

    return occurrences.entrySet().stream()
        .map(
            entry ->
                new RecipeIngredientInfo(
                    entry.getKey().itemIds(), entry.getKey().tagIds(), entry.getValue()))
        .toList();
  }

  private static void collectTagIds(SlotDisplay display, TreeSet<String> tagIds) {
    if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
      tagIds.add(tagDisplay.tag().location().toString());
    } else if (display instanceof SlotDisplay.Composite composite) {
      composite.contents().forEach(child -> collectTagIds(child, tagIds));
    } else if (display instanceof SlotDisplay.WithRemainder withRemainder) {
      collectTagIds(withRemainder.input(), tagIds);
    } else if (display instanceof SlotDisplay.OnlyWithComponent withComponent) {
      collectTagIds(withComponent.source(), tagIds);
    } else if (display instanceof SlotDisplay.WithAnyPotion withPotion) {
      collectTagIds(withPotion.display(), tagIds);
    }
  }

  private static Optional<ItemStack> resultFor(
      Recipe<?> recipe, Identifier targetId, ContextMap displayContext) {
    return recipe.display().stream()
        .map(RecipeDisplay::result)
        .flatMap(display -> display.resolveForStacks(displayContext).stream())
        .filter(stack -> !stack.isEmpty())
        .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetId))
        .findFirst();
  }

  private static boolean matches(String[] terms, String itemId, String displayName) {
    String haystack =
        (itemId + " " + itemId.replace('_', ' ').replace(':', ' ') + " " + displayName)
            .toLowerCase(Locale.ROOT);
    for (String term : terms) {
      if (!haystack.contains(term)) {
        return false;
      }
    }
    return true;
  }

  private static <T> ToolResult<T> failure(ToolErrorCode code, String message, boolean retryable) {
    return ToolResult.failure(ToolError.of(code, message, retryable));
  }

  private record RecipeReadContext(
      MinecraftServer server, ResourceKey<Level> dimension, Identifier targetId) {}

  private record IngredientAlternatives(List<String> itemIds, List<String> tagIds) {}
}
