import React from 'react';
import { Link } from 'react-router-dom';
import './ProductTile.css';

const ProductTile = ({ id, productName, imageURL, price }) => {
    return (
        // Wrapproduct-tile in a Link component to navigate to the product details page
        <Link to={`/products/${id}`} className="product-tile">
            <img src={imageURL} alt={productName} className="product-image" />
            <div className="product-name">{productName}</div>
            <div className="product-price">${price.toFixed(2)}</div>
        </Link>
    );
};

export default ProductTile;
