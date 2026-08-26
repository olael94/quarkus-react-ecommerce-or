import { useCart } from '../../context/CartContext';
import { Link } from 'react-router-dom';
import './CartDrawer.css';

function CartDrawer() {
    const { items, estimatedTotal, isDrawerOpen, closeDrawer, updateQuantity, removeItem } =
        useCart();

    return (
        <>
            <div
                className={`cart-drawer-backdrop ${isDrawerOpen ? 'open' : ''}`}
                onClick={closeDrawer}
            />
            <div className={`cart-drawer ${isDrawerOpen ? 'open' : ''}`}>
                <div className="cart-drawer-header">
                    <h2>Shopping Cart</h2>
                    <button type="button" onClick={closeDrawer} className="cart-drawer-close">
                        &times;
                    </button>
                </div>

                {items.length === 0 ? (
                    <p className="cart-drawer-empty">Your cart is empty</p>
                ) : (
                    <>
                        <div className="cart-drawer-items">
                            {items.map((item) => (
                                <div key={item.productId} className="cart-drawer-item">
                                    <img
                                        src={item.imageURL}
                                        alt={item.productName}
                                        className="cart-drawer-item-image"
                                    />
                                    <div className="cart-drawer-item-details">
                                        <h3 className="cart-drawer-item-name">
                                            {item.productName}
                                        </h3>
                                        <p className="cart-drawer-item-price">
                                            ${item.price.toFixed(2)}
                                        </p>
                                        <div className="cart-drawer-item-stepper">
                                            <button
                                                type="button"
                                                onClick={() =>
                                                    updateQuantity(
                                                        item.productId,
                                                        item.quantity - 1
                                                    )
                                                }
                                                className="cart-drawer-stepper-button"
                                            >
                                                -
                                            </button>
                                            <span>{item.quantity}</span>
                                            <button
                                                type="button"
                                                onClick={() =>
                                                    updateQuantity(
                                                        item.productId,
                                                        item.quantity + 1
                                                    )
                                                }
                                                className="cart-drawer-stepper-button"
                                            >
                                                +
                                            </button>
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => removeItem(item.productId)}
                                        className="cart-drawer-remove"
                                    >
                                        Remove
                                    </button>
                                </div>
                            ))}
                        </div>
                        <div className="cart-drawer-footer">
                            <p className="cart-drawer-total">
                                <strong>Estimated Total:</strong> ${estimatedTotal.toFixed(2)}
                            </p>
                            <Link
                                to="/cart"
                                onClick={closeDrawer}
                                className="cart-drawer-go-to-cart"
                            >
                                Go to Cart
                            </Link>
                        </div>
                    </>
                )}
            </div>
        </>
    );
}

export default CartDrawer;
