import '../styles/CartPage.css';
import { useCart } from '../context/CartContext';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useState } from 'react';
import { getCsrfToken } from '../context/AuthContext';
import { API_URL } from '../config';

function CartPage() {
    const { items, estimatedTotal, updateQuantity, removeItem, clearCart } = useCart();
    const { user } = useAuth();
    const [guestEmail, setGuestEmail] = useState('');
    const [checkoutError, setCheckoutError] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleCheckout = async () => {
        if (!user && !guestEmail.trim()) {
            setCheckoutError('Please enter your email to continue as a guest.');
            return;
        }

        setCheckoutError('');
        setIsSubmitting(true); // Disables the button during submission

        const requestBody = {
            userId: user ? user.id : null,
            guestEmail: user ? null : guestEmail,
            items: items.map((item) => ({
                productId: item.productId,
                quantity: item.quantity,
            })),
        };

        const response = await fetch(`${API_URL}/api/orders`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken(),
            },
            body: JSON.stringify(requestBody),
        });

        const data = await response.json();

        if (!response.ok) {
            setCheckoutError(data.message); // Display the error message from the server
            setIsSubmitting(false);
            return;
        }

        //Must clear the cart before redirecting to the checkout page
        clearCart();
        window.location.href = data.checkoutUrl;
    };

    if (items.length === 0) {
        return (
            <div className="CartPage-container">
                <h1>Your cart</h1>
                <p className="cart-page-empty">
                    Your cart is empty. <Link to="/">Continue shopping</Link>
                </p>
            </div>
        );
    }

    return (
        <div className="CartPage-container">
            <h1>Your Cart</h1>
            <div className="cart-page-items">
                {items.map((item) => (
                    <div key={item.productId} className="cart-page-item">
                        <img
                            src={item.imageURL}
                            alt={item.productName}
                            className="cart-page-item-image"
                        />
                        <div className="cart-page-item-details">
                            <h2 className="cart-page-item-name">{item.productName}</h2>
                            <p className="cart-page-item-price">${item.price.toFixed(2)}</p>
                        </div>
                        <div className="cart-page-item-stepper">
                            <button
                                type="button"
                                onClick={() => updateQuantity(item.productId, item.quantity - 1)}
                                className="cart-page-stepper-button"
                            >
                                -
                            </button>
                            <span>{item.quantity}</span>
                            <button
                                type="button"
                                onClick={() => updateQuantity(item.productId, item.quantity + 1)}
                                className="cart-page-stepper-button"
                            >
                                +
                            </button>
                        </div>
                        <p className="cart-page-item-subtotal">
                            ${(item.price * item.quantity).toFixed(2)}
                        </p>
                        <button
                            type="button"
                            onClick={() => removeItem(item.productId)}
                            className="cart-page-remove"
                        >
                            Remove
                        </button>
                    </div>
                ))}
            </div>
            <div className="cart-page-summary">
                <h2 className="cart-page-total">Estimated Total</h2>
                <p>${estimatedTotal.toFixed(2)}</p>
            </div>
            {!user && (
                <div className="cart-page-guest-email">
                    <p className="cart-page-guest-notice">
                        You&apos;re checking out as a guest. Enter your email below so we can send
                        your order confirmation, or{' '}
                        <Link to="/account">sign in or create an account</Link>.
                    </p>
                    <label htmlFor="guestEmail">Email (for order confirmation)</label>
                    <input
                        id="guestEmail"
                        type="email"
                        value={guestEmail}
                        onChange={(e) => setGuestEmail(e.target.value)}
                        placeholder="you@example.com"
                    />
                </div>
            )}

            {checkoutError && <p className="cart-page-error">{checkoutError}</p>}
            <button
                type="button"
                onClick={handleCheckout}
                disabled={isSubmitting}
                className="cart-page-checkout-button"
            >
                {isSubmitting ? 'Processing...' : 'Proceed to Checkout'}
            </button>
        </div>
    );
}

export default CartPage;
