import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { API_URL } from '../config';
import { useCart } from '../context/CartContext';
import '../styles/ProductDetailPage.css';

function ProductDetailPage() {
    // Get the product ID from the URL
    const { id } = useParams();
    // use null to get 1 product not a products list
    const [product, setProduct] = useState(null);
    const [quantity, setQuantity] = useState(1);
    const { addItem } = useCart();

    // Fetch the product details from the server
    useEffect(() => {
        fetch(`${API_URL}/api/products/${id}`)
            .then((res) => res.json())
            .then((data) => setProduct(data))
            .catch((error) => console.error('Error fetching product:', error));
    }, [id]);

    // Check if product is null before rendering
    if (!product) {
        return <p>Loading...</p>;
    }

    // Check if the product is out of stock
    const outOfStock = product.quantity <= 0;

    const handleAddToCart = () => {
        addItem(product, quantity);
    };

    const handleDecrement = () => {
        setQuantity((currentQuantity) => Math.max(1, currentQuantity - 1));
    };

    const handleIncrement = () => {
        setQuantity((currentQuantity) => Math.min(product.quantity, currentQuantity + 1));
    };

    return (
        <div className="ProductDetailPage-container">
            <div className="detail-left">
                <img src={product.imageURL} alt={product.productName} className="detail-image" />
            </div>
            <div className="detail-right">
                <h1 className="detail-name">{product.productName}</h1>
                <p className="detail-price">${product.price.toFixed(2)}</p>
                <p className="detail-description">{product.description}</p>
                <p className="detail-quantity">In stock: {product.quantity}</p>

                {outOfStock ? (
                    <p className="detail-out-of-stock">Out of stock</p>
                ) : (
                    <div className="detail-add-to-cart">
                        <label>Quantity:</label>
                        <div className="quantity-stepper">
                            <button onClick={handleDecrement} className="quantity-stepper-button">
                                -
                            </button>
                            <span className="quantity-stepper-value">{quantity}</span>
                            <button
                                type="button"
                                onClick={handleIncrement}
                                className="quantity-stepper-button"
                            >
                                +
                            </button>
                        </div>
                        <button onClick={handleAddToCart} className="detail-add-to-cart-button">
                            Add to Cart
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

export default ProductDetailPage;
