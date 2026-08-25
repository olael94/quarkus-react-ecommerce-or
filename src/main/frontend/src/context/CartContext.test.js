import { renderHook, act } from '@testing-library/react';
import { CartProvider, useCart } from './CartContext';

// eslint-disable-next-line react/prop-types
const wrapper = ({ children }) => <CartProvider>{children}</CartProvider>;

const testProduct = {
    id: 1,
    productName: 'Test Widget',
    imageURL: 'https://example.com/widget.png',
    price: 10.0,
};

const secondTestProduct = {
    id: 2,
    productName: 'Test Gadget',
    imageURL: 'https://example.com/gadget.png',
    price: 5.5,
};

describe('CartContext', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    test('starts with an empty cart', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        expect(result.current.items).toEqual([]);
        expect(result.current.itemCount).toBe(0);
        expect(result.current.estimatedTotal).toBe(0);
    });

    test('addItem adds a new product to the cart', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 2);
        });

        expect(result.current.items).toHaveLength(1);
        expect(result.current.items[0]).toMatchObject({
            productId: 1,
            productName: 'Test Widget',
            quantity: 2,
        });
    });

    test('addItem merges quantity when the same product is added again', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 2);
        });
        act(() => {
            result.current.addItem(testProduct, 3);
        });

        expect(result.current.items).toHaveLength(1);
        expect(result.current.items[0].quantity).toBe(5);
    });

    test('addItem opens the cart drawer', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        expect(result.current.isDrawerOpen).toBe(false);

        act(() => {
            result.current.addItem(testProduct, 1);
        });

        expect(result.current.isDrawerOpen).toBe(true);
    });

    test('closeDrawer closes the drawer', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 1);
        });
        act(() => {
            result.current.closeDrawer();
        });

        expect(result.current.isDrawerOpen).toBe(false);
    });

    test('removeItem removes the specified product', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 1);
            result.current.addItem(secondTestProduct, 1);
        });
        act(() => {
            result.current.removeItem(testProduct.id);
        });

        expect(result.current.items).toHaveLength(1);
        expect(result.current.items[0].productId).toBe(secondTestProduct.id);
    });

    test('updateQuantity changes an item quantity', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 1);
        });
        act(() => {
            result.current.updateQuantity(testProduct.id, 5);
        });

        expect(result.current.items[0].quantity).toBe(5);
    });

    test('updateQuantity removes the item when quantity drops to 0', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 1);
        });
        act(() => {
            result.current.updateQuantity(testProduct.id, 0);
        });

        expect(result.current.items).toHaveLength(0);
    });

    test('clearCart empties the cart', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 1);
            result.current.addItem(secondTestProduct, 1);
        });
        act(() => {
            result.current.clearCart();
        });

        expect(result.current.items).toEqual([]);
    });

    test('itemCount and estimatedTotal reflect multiple items and quantities', () => {
        const { result } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 2); // 2 x $10.00 = $20.00
            result.current.addItem(secondTestProduct, 3); // 3 x $5.50 = $16.50
        });

        expect(result.current.itemCount).toBe(5);
        expect(result.current.estimatedTotal).toBeCloseTo(36.5);
    });

    test('cart persists to localStorage and is loaded back by a fresh provider', () => {
        const { result, unmount } = renderHook(() => useCart(), { wrapper });

        act(() => {
            result.current.addItem(testProduct, 2);
        });

        unmount();

        const { result: secondResult } = renderHook(() => useCart(), { wrapper });

        expect(secondResult.current.items).toHaveLength(1);
        expect(secondResult.current.items[0]).toMatchObject({
            productId: 1,
            quantity: 2,
        });
    });
});
