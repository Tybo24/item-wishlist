package com.itemwishlist;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.NumberFormatter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

class ItemWishlistPanel extends PluginPanel
{
	private static final Color COIN_COLOR = new Color(255, 214, 0);
	private static final Color ADD_COLOR = new Color(0, 200, 83);
	private static final Color REMOVE_COLOR = new Color(220, 60, 60);

	private final ItemWishlistPlugin plugin;
	private final ItemManager itemManager;

	private final IconTextField searchBar = new IconTextField();
	private final JLabel totalLabel = new JLabel("0 gp");
	private final JLabel countLabel = new JLabel("0 items");
	private final JPanel searchResultsHeader = new JPanel(new BorderLayout());
	private final JPanel searchResults = new JPanel(new GridLayout(0, 1, 0, 5));
	private final JPanel wishlistRows = new JPanel(new GridLayout(0, 1, 0, 5));
	private final JLabel emptyLabel = new JLabel("Search for an item above to add it.");

	private List<WishlistItem> currentResults = Collections.emptyList();
	private Map<Integer, Long> currentResultPrices = Collections.emptyMap();
	private Set<Integer> wishlistIds = Collections.emptySet();

	private List<WishlistItem> shownItems = Collections.emptyList();
	private Map<Integer, Long> shownPrices = Collections.emptyMap();

	ItemWishlistPanel(ItemWishlistPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());

		final JPanel content = new JPanel(new DynamicGridLayout(0, 1, 0, 5));

		content.add(buildTotalPanel());

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.addActionListener(e -> plugin.search(searchBar.getText()));
		searchBar.addClearListener(() -> plugin.search(""));
		content.add(searchBar);

		final JLabel searchTitle = new JLabel("Search results");
		searchTitle.setFont(FontManager.getRunescapeBoldFont());
		searchTitle.setForeground(Color.WHITE);
		final JLabel clearResults = new JLabel("Clear");
		clearResults.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		clearResults.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		clearResults.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				searchBar.setText("");
				showSearchResults(Collections.emptyList(), Collections.emptyMap());
			}
		});
		searchResultsHeader.add(searchTitle, BorderLayout.WEST);
		searchResultsHeader.add(clearResults, BorderLayout.EAST);
				searchResultsHeader.setVisible(false);
		searchResults.setVisible(false);
		content.add(searchResultsHeader);
		content.add(searchResults);

		final JLabel wishlistTitle = new JLabel("Wishlist");
		wishlistTitle.setFont(FontManager.getRunescapeBoldFont());
		wishlistTitle.setForeground(Color.WHITE);

		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setFont(FontManager.getRunescapeSmallFont());

		final JPanel wishlistSection = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		wishlistSection.add(wishlistTitle);
		wishlistSection.add(emptyLabel);
		wishlistSection.add(wishlistRows);
		content.add(wishlistSection);

		add(content, BorderLayout.NORTH);
	}

	@Override
	public void onActivate()
	{
		// Prices may have changed since the panel was last shown
		plugin.refresh();
	}

	void showSearchResults(List<WishlistItem> results, Map<Integer, Long> prices)
	{
		currentResults = results;
		currentResultPrices = prices;
		renderSearchResults();
	}

	private void renderSearchResults()
	{
		searchResults.removeAll();
		for (WishlistItem result : currentResults)
		{
			final long price = currentResultPrices.getOrDefault(result.getId(), 0L);
			if (wishlistIds.contains(result.getId()))
			{
				searchResults.add(buildRow(result.getId(), result.getName(), priceLabel(price),
					buildButton("Added", ColorScheme.LIGHT_GRAY_COLOR, "Already on your wishlist", null)));
			}
			else
			{
				searchResults.add(buildRow(result.getId(), result.getName(), priceLabel(price),
					buildButton("+", ADD_COLOR, "Add to wishlist", () -> plugin.addItem(result.getId(), result.getName()))));
			}
		}
		searchResultsHeader.setVisible(!currentResults.isEmpty());
		searchResults.setVisible(!currentResults.isEmpty());
		revalidate();
		repaint();
	}

	void showWishlist(List<WishlistItem> items, Map<Integer, Long> prices)
	{
		shownItems = items;
		shownPrices = prices;

		wishlistRows.removeAll();
		wishlistIds = new HashSet<>();
		for (WishlistItem item : items)
		{
			wishlistIds.add(item.getId());
			wishlistRows.add(buildWishlistRow(item, prices.getOrDefault(item.getId(), 0L)));
		}

		emptyLabel.setVisible(items.isEmpty());
		updateTotals();
		renderSearchResults();
	}

	private void updateTotals()
	{
		long total = 0;
		long count = 0;
		for (WishlistItem item : shownItems)
		{
			total += linePrice(shownPrices.getOrDefault(item.getId(), 0L), item.getQuantity());
			count += item.getQuantity();
		}

		totalLabel.setText(QuantityFormatter.formatNumber(total) + " gp");
		countLabel.setText(count == 1 ? "1 item" : QuantityFormatter.formatNumber(count) + " items");
	}

	private static long linePrice(long unitPrice, int quantity)
	{
		return unitPrice * quantity;
	}

	private JPanel buildTotalPanel()
	{
		final JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		final JLabel title = new JLabel("Total value");
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		title.setFont(FontManager.getRunescapeSmallFont());

		totalLabel.setForeground(COIN_COLOR);
		totalLabel.setFont(FontManager.getRunescapeBoldFont());

		countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		countLabel.setFont(FontManager.getRunescapeSmallFont());

		final JPanel left = new JPanel(new GridLayout(2, 1));
		left.setOpaque(false);
		left.add(title);
		left.add(totalLabel);

		panel.add(left, BorderLayout.CENTER);
		panel.add(countLabel, BorderLayout.EAST);
		return panel;
	}

	private JPanel buildWishlistRow(WishlistItem item, long unitPrice)
	{
		final JLabel priceLabel = priceLabel(linePrice(unitPrice, item.getQuantity()));
		priceLabel.setToolTipText(formatPrice(linePrice(unitPrice, item.getQuantity())) + " (" + formatPrice(unitPrice) + " each)");

		final JSpinner quantity = buildQuantitySpinner(item, unitPrice, priceLabel);
		final JLabel remove = buildButton("X", REMOVE_COLOR, "Remove from wishlist", () -> plugin.removeItem(item.getId()));

		// Name and remove button on the first line, price and quantity on the second, so long names keep their room
		final JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(nameLabel(item.getName()), BorderLayout.CENTER);
		top.add(remove, BorderLayout.EAST);

		final JPanel bottom = new JPanel(new BorderLayout(6, 0));
		bottom.setOpaque(false);
		bottom.add(priceLabel, BorderLayout.CENTER);
		bottom.add(quantity, BorderLayout.EAST);

		final JPanel text = new JPanel(new BorderLayout(0, 2));
		text.setOpaque(false);
		text.add(top, BorderLayout.NORTH);
		text.add(bottom, BorderLayout.CENTER);

		final JPanel row = newRow(item.getId());
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private JSpinner buildQuantitySpinner(WishlistItem item, long unitPrice, JLabel priceLabel)
	{
		final JSpinner spinner = new JSpinner(new SpinnerNumberModel(item.getQuantity(), 1, Integer.MAX_VALUE, 1));
		spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
		spinner.setPreferredSize(new Dimension(68, 22));
		spinner.setToolTipText("Quantity");

		// Update as the user types, rather than waiting for Enter or focus loss
		final JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		((NumberFormatter) field.getFormatter()).setCommitsOnValidEdit(true);

		spinner.addChangeListener(e ->
		{
			final int quantity = (Integer) spinner.getValue();
			item.setQuantity(quantity);
			plugin.setQuantity(item.getId(), quantity);
			priceLabel.setText(formatPrice(linePrice(unitPrice, quantity)));
			priceLabel.setToolTipText(formatPrice(linePrice(unitPrice, quantity)) + " (" + formatPrice(unitPrice) + " each)");
			updateTotals();
		});
		return spinner;
	}

	private JLabel priceLabel(long price)
	{
		final JLabel label = new JLabel(formatPrice(price));
		label.setForeground(COIN_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static String formatPrice(long price)
	{
		return QuantityFormatter.formatNumber(price) + " gp";
	}

	private JLabel buildButton(String text, Color color, String tooltip, Runnable onClick)
	{
		final JLabel button = new JLabel(text);
		button.setForeground(color);
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setToolTipText(tooltip);
		if (onClick != null)
		{
			button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			button.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			});
		}
		return button;
	}

	private JPanel newRow(int itemId)
	{
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 8));

		final JLabel icon = new JLabel();
		final AsyncBufferedImage image = itemManager.getImage(itemId);
		image.addTo(icon);
		row.add(icon, BorderLayout.WEST);
		return row;
	}

	private static JLabel nameLabel(String name)
	{
		final JLabel label = new JLabel(name);
		label.setForeground(Color.WHITE);
		label.setToolTipText(name);
		return label;
	}

	private JPanel buildRow(int itemId, String name, JLabel priceLabel, JComponent trailing)
	{
		final JPanel row = newRow(itemId);

		final JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		text.add(nameLabel(name));
		text.add(priceLabel);
		row.add(text, BorderLayout.CENTER);

		row.add(trailing, BorderLayout.EAST);
		return row;
	}
}
