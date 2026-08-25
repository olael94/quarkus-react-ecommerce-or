import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CartPage from './CartPage';
import { AuthProvider } from '../context/AuthContext';
import { CartProvider } from '../context/CartContext';

function renderCartPage() {
    return render(
        <MemoryRouter>
            <AuthProvider>
                <CartProvider>
                    <CartPage />
                </CartProvider>
            </AuthProvider>
        </MemoryRouter>
    );
}

const cartItem = {
    productId: 1,
    productName: 'Test Widget',
    imageURL: 'https://example.com/widget.png',
    price: 10.0,
    quantity: 2,
};

// Every test here is a guest (not logged in) scenario unless a test overrides
// the /api/users/me mock - CartPage's own logic branches on whether a user is
// present, so "not logged in" is the more interesting default to exercise.
function mockFetchNotLoggedIn() {
    global.fetch = jest.fn((url) => {
        if (url.includes('/api/users/me')) {
            return Promise.resolve({ ok: false });
        }
        return Promise.reject(new Error(`Unhandled fetch in test: ${url}`));
    });
}

describe('CartPage', () => {
    beforeEach(() => {
        localStorage.clear();
        mockFetchNotLoggedIn();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('shows the empty-cart message when there are no items', async () => {
        renderCartPage();

        expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
    });

    test('renders a cart item loaded from localStorage', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        renderCartPage();

        // Scoped to the item row: with only one item in the cart, the item
        // subtotal and the cart-wide total both read "$20.00", so an
        // unscoped query would match two elements. There's no accessible
        // role/label distinguishing a single row from the page around it,
        // so this falls back to direct node access instead.
        // eslint-disable-next-line testing-library/no-node-access
        const itemRow = (await screen.findByText('Test Widget')).closest('.cart-page-item');
        expect(within(itemRow).getByText('$20.00')).toBeInTheDocument(); // subtotal: 2 x $10.00
    });

    test('clicking + increases the quantity and updates the subtotal', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        renderCartPage();

        // Scoped to the item row - see the note in the previous test.
        // eslint-disable-next-line testing-library/no-node-access
        const itemRow = (await screen.findByText('Test Widget')).closest('.cart-page-item');
        fireEvent.click(within(itemRow).getByRole('button', { name: '+' }));

        expect(await within(itemRow).findByText('3')).toBeInTheDocument();
        // Scoped to the item row - see the note in the previous test.
        expect(within(itemRow).getByText('$30.00')).toBeInTheDocument();
    });

    test('clicking Remove empties the cart', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        renderCartPage();

        await screen.findByText('Test Widget');
        fireEvent.click(screen.getByRole('button', { name: /remove/i }));

        expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
    });

    test('shows the guest checkout notice and email field when not logged in', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        renderCartPage();

        expect(await screen.findByText(/checking out as a guest/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    });

    test('checkout succeeds: clears the cart and redirects to the returned checkoutUrl', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        global.fetch = jest.fn((url) => {
            if (url.includes('/api/users/me')) {
                return Promise.resolve({ ok: false });
            }
            if (url.includes('/api/orders')) {
                return Promise.resolve({
                    ok: true,
                    json: () =>
                        Promise.resolve({
                            checkoutUrl: 'https://checkout.stripe.com/fake-session',
                        }),
                });
            }
            return Promise.reject(new Error(`Unhandled fetch in test: ${url}`));
        });

        // jsdom doesn't allow directly assigning window.location.href in newer
        // versions, so the standard workaround is to replace the whole object
        // with a writable stand-in before the redirect happens.
        Object.defineProperty(window, 'location', {
            writable: true,
            value: { href: '' },
        });

        renderCartPage();

        await screen.findByText('Test Widget');
        fireEvent.change(screen.getByLabelText(/email/i), {
            target: { value: 'guest@example.com' },
        });
        fireEvent.click(screen.getByRole('button', { name: /proceed to checkout/i }));

        await waitFor(() => {
            expect(window.location.href).toBe('https://checkout.stripe.com/fake-session');
        });
        await waitFor(() => {
            expect(localStorage.getItem('cart')).toBe(JSON.stringify([]));
        });
    });

    test('checkout failure shows the server error message and keeps the cart intact', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        global.fetch = jest.fn((url) => {
            if (url.includes('/api/users/me')) {
                return Promise.resolve({ ok: false });
            }
            if (url.includes('/api/orders')) {
                return Promise.resolve({
                    ok: false,
                    json: () =>
                        Promise.resolve({ message: 'Not enough stock for product: Test Widget' }),
                });
            }
            return Promise.reject(new Error(`Unhandled fetch in test: ${url}`));
        });

        renderCartPage();

        await screen.findByText('Test Widget');
        fireEvent.change(screen.getByLabelText(/email/i), {
            target: { value: 'guest@example.com' },
        });
        fireEvent.click(screen.getByRole('button', { name: /proceed to checkout/i }));

        expect(
            await screen.findByText('Not enough stock for product: Test Widget')
        ).toBeInTheDocument();
        expect(screen.getByText('Test Widget')).toBeInTheDocument();
    });

    test('checkout is blocked with a validation message when a guest submits without an email', async () => {
        localStorage.setItem('cart', JSON.stringify([cartItem]));

        renderCartPage();

        await screen.findByText('Test Widget');
        fireEvent.click(screen.getByRole('button', { name: /proceed to checkout/i }));

        expect(await screen.findByText(/please enter your email/i)).toBeInTheDocument();
        // No order request should have been attempted - only the /api/users/me
        // call from AuthProvider's initial fetch.
        expect(global.fetch).toHaveBeenCalledTimes(1);
    });
});
