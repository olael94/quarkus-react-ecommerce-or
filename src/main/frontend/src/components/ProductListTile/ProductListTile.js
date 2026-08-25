import React from 'react';
import { Link } from 'react-router-dom';
import './ProductListTile.css';
import { useCart } from '../../context/CartContext';

const ProductListTile = ({ id, productName, imageURL, price }) => {
    const { addItem } = useCart();

    const handleAddToCart = (event) => {
        event.preventDefault();
        event.stopPropagation();
        addItem({ id, productName, imageURL, price }, 1);
    };

    return (
        <Link to={`/products/${id}`} className="productList-tile">
            <div className="left-container">
                <img src={imageURL} alt={productName} className="productList-image" />
            </div>
            <div className="right-container">
                <div className="productList-name">{productName}</div>
                <div className="productList-price">${price.toFixed(2)}</div>
                <button onClick={handleAddToCart} className="productList-add-to-cart">
                    Add to Cart
                </button>
            </div>
        </Link>
    );
};

export default ProductListTile;
