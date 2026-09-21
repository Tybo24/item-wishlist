package com.itemwishlist;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Item Wishlist",
	description = "Keep a wishlist of items in a side panel and see their combined Grand Exchange price",
	tags = {"wishlist", "item", "price", "grand exchange", "ge", "total"}
)
public class ItemWishlistPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "item-wishlist";
	private static final String CONFIG_KEY_ITEMS = "items";
	private static final int MAX_SEARCH_RESULTS = 25;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private final List<WishlistItem> items = new CopyOnWriteArrayList<>();

	private ItemWishlistPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		items.clear();
		items.addAll(loadItems());

		panel = new ItemWishlistPanel(this, itemManager);

		final BufferedImage icon = ImageUtil.loadImageResource(ItemWishlistPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Item Wishlist")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		refresh();
		log.debug("Item Wishlist started with {} items", items.size());
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		items.clear();
		log.debug("Item Wishlist stopped");
	}

	/**
	 * Searches tradeable items by name and shows the results in the panel.
	 */
	void search(String query)
	{
		final String trimmed = query.trim();
		if (trimmed.isEmpty())
		{
			showSearchResults(new ArrayList<>(), new HashMap<>());
			return;
		}

		clientThread.invoke(() ->
		{
			final List<WishlistItem> matches = new ArrayList<>();
			final Map<Integer, Integer> prices = new HashMap<>();
			for (ItemPrice result : itemManager.search(trimmed))
			{
				if (matches.size() >= MAX_SEARCH_RESULTS)
				{
					break;
				}
				matches.add(new WishlistItem(result.getId(), result.getName()));
				prices.put(result.getId(), itemManager.getItemPrice(result.getId()));
			}
			showSearchResults(matches, prices);
		});
	}

	void addItem(int id, String name)
	{
		for (WishlistItem item : items)
		{
			if (item.getId() == id)
			{
				return;
			}
		}

		items.add(new WishlistItem(id, name));
		saveItems();
		refresh();
	}

	void removeItem(int id)
	{
		if (items.removeIf(item -> item.getId() == id))
		{
			saveItems();
			refresh();
		}
	}

	/**
	 * Updates and saves an item's quantity. The panel updates its own copy, so this does not refresh it.
	 */
	void setQuantity(int id, int quantity)
	{
		final int clamped = Math.max(1, quantity);
		for (WishlistItem item : items)
		{
			if (item.getId() == id)
			{
				item.setQuantity(clamped);
				saveItems();
				return;
			}
		}
	}

	/**
	 * Looks up current prices for every wishlisted item and pushes them to the panel.
	 */
	void refresh()
	{
		// Copies, so the panel can edit quantities without touching the saved list
		final List<WishlistItem> snapshot = new ArrayList<>();
		for (WishlistItem item : items)
		{
			snapshot.add(new WishlistItem(item.getId(), item.getName(), item.getQuantity()));
		}

		clientThread.invoke(() ->
		{
			final Map<Integer, Integer> prices = new HashMap<>();
			for (WishlistItem item : snapshot)
			{
				prices.put(item.getId(), itemManager.getItemPrice(item.getId()));
			}

			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showWishlist(snapshot, prices);
				}
			});
		});
	}

	private void showSearchResults(List<WishlistItem> results, Map<Integer, Integer> prices)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showSearchResults(results, prices);
			}
		});
	}

	private List<WishlistItem> loadItems()
	{
		final String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_ITEMS);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}

		try
		{
			final WishlistItem[] loaded = gson.fromJson(json, WishlistItem[].class);
			final List<WishlistItem> result = new ArrayList<>();
			if (loaded != null)
			{
				for (WishlistItem item : loaded)
				{
					if (item != null && item.getName() != null)
					{
						item.setQuantity(Math.max(1, item.getQuantity()));
						result.add(item);
					}
				}
			}
			return result;
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Could not read saved wishlist, starting with an empty one", e);
			return new ArrayList<>();
		}
	}

	private void saveItems()
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_ITEMS, gson.toJson(items));
	}
}
