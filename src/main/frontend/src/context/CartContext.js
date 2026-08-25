import { createContext, useContext, useEffect, useState } from 'react';

const CartContext = createContext(); // CartContext creates an empty container to hold the cart data

const CART_STORAGE_KEY = 'cart';

/**
 * Reads saved items in localStorage the last time the user visited the site.
 * The cart survives page reloads.
 * If there are no saved items, returns an empty array.
 * @returns {any|*[]}
 */
function loadCartFromStorage() {
    try {
        const savedCart = localStorage.getItem(CART_STORAGE_KEY);
        if (savedCart) {
            return JSON.parse(savedCart);
        }
    } catch (error) {
        console.error('Error loading cart from storage:', error);
    }
    return [];
}

export function CartProvider({ children }) {
    const [items, setItems] = useState(loadCartFromStorage);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);

    const openDrawer = () => {
        setIsDrawerOpen(true);
    };
    const closeDrawer = () => {
        setIsDrawerOpen(false);
    };

    // Every time the cart changes, save the new version to local storage
    useEffect(() => {
        localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
    }, [items]);

    const addItem = (product, quantityToAdd) => {
        setItems((currentItems) => {
            const existingItem = currentItems.find((item) => item.productId === product.id);
            if (existingItem) {
                // If the item already exists, update its quantity instead of adding a new row
                return currentItems.map((item) =>
                    item.productId === product.id
                        ? { ...item, quantity: item.quantity + quantityToAdd }
                        : item
                );
            }

            const newItem = {
                productId: product.id,
                productName: product.productName,
                imageURL: product.imageURL,
                price: product.price, // Display estimate only - server recomputes the real price at checkout
                quantity: quantityToAdd,
            };
            return [...currentItems, newItem];
        });
        openDrawer(); // Open the cart drawer when an item is added
    };

    const removeItem = (productId) => {
        setItems((currentItems) => currentItems.filter((item) => item.productId !== productId));
    };

    const updateQuantity = (productId, newQuantity) => {
        // Dragging quantity down to 0 removes the item, matching typical shopping cart behavior
        if (newQuantity <= 0) {
            removeItem(productId);
            return;
        }

        setItems((currentItems) =>
            currentItems.map((item) =>
                item.productId === productId ? { ...item, quantity: newQuantity } : item
            )
        );
    };

    const clearCart = () => {
        setItems([]);
    };

    // estimatedTotal is client-side only, for display before checkout - createOrder always
    // recomputes the real price at checkout.
    let itemCount = 0;
    let estimatedTotal = 0;
    for (const item of items) {
        itemCount += item.quantity;
        estimatedTotal += item.price * item.quantity;
    }

    return (
        <CartContext.Provider
            value={{
                items,
                addItem,
                removeItem,
                updateQuantity,
                clearCart,
                itemCount,
                estimatedTotal,
                isDrawerOpen,
                openDrawer,
                closeDrawer,
            }}
        >
            {children}
        </CartContext.Provider>
    );
}

export function useCart() {
    return useContext(CartContext);
}
