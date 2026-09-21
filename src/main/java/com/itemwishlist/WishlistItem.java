package com.itemwishlist;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An item on the wishlist. Only the id, name and quantity are persisted; prices are looked up live.
 */
@Getter
@NoArgsConstructor
public class WishlistItem
{
	private int id;
	private String name;

	@Setter
	private volatile int quantity = 1;

	public WishlistItem(int id, String name)
	{
		this.id = id;
		this.name = name;
	}

	public WishlistItem(int id, String name, int quantity)
	{
		this(id, name);
		this.quantity = quantity;
	}
}
