import '../styles/CartPage.css';
import { useCart } from '../context/CartContext';
import { Link } from 'react-router-dom';

function CartPage() {
    const { items, estimatedTotal, updateQuantity, removeItem } = useCart();

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
        </div>
    );
}

export default CartPage;
