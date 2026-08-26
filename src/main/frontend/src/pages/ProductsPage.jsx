import ProductListTile from '../components/ProductListTile/ProductListTile';
import React, { useEffect, useState } from 'react';
import { API_URL } from '../config';
import '../styles/ProductsPage.css'; // Import the CSS

function ProductsPage() {
    // Define a state variable 'products' to store the list of products fetched from the server.
    const [products, setProducts] = useState([]);
    // Use the useEffect hook to fetch the list of products from the server when the component mounts.
    useEffect(() => {
        fetch(`${API_URL}/api/products`)
            .then((res) => res.json())
            .then((data) => {
                console.log('Fetched products:', data); // Check the product field names here
                setProducts(data);
            })
            .catch((error) => console.error('Error fetching products:', error));
    }, []);

    return (
        <div className="ProductsPage-container">
            <div className="product-list-detailed">
                {products.map((product) => (
                    <ProductListTile
                        key={product.id}
                        id={product.id} // This is the accessible id prop for each ProductListTile component
                        productName={product.productName}
                        imageURL={product.imageURL}
                        price={product.price}
                    />
                ))}
            </div>
        </div>
    );
}

export default ProductsPage;
