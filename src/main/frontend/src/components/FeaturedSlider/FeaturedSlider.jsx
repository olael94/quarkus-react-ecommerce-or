import React, { useEffect, useState } from 'react';
import Slider from 'react-slick';
import { useNavigate } from 'react-router-dom';
import { API_URL } from '../../config';
import './FeaturedSlider.css';

const FeaturedSlider = () => {
    const navigate = useNavigate();
    const [products, setProducts] = useState([]);
    const bannerGradients = [
        'linear-gradient(135deg, #1a2b4c, #0d3b3a)',
        'linear-gradient(135deg, #2d1b4e, #3b1a1a)',
        'linear-gradient(135deg, #0d3b3a, #1a3b2a)',
    ];

    // Fetch featured products from the server
    useEffect(() => {
        fetch(`${API_URL}/api/products`)
            .then((res) => res.json())
            .then((data) => setProducts(data.slice(0, 3)))
            .catch((error) => console.error('Error fetching featured products:', error));
    }, []);

    const handleButtonClick = (id) => {
        navigate(`/products/${id}`); // Navigate to the product link
    };

    // Settings for the slider
    const settings = {
        dots: true,
        infinite: true,
        speed: 1200,
        slidesToShow: 1,
        slidesToScroll: 1,
        autoplay: true,
        autoplaySpeed: 8000,
        fade: true,
        cssEase: 'linear',
    };

    return (
        <div className="slider-container">
            <Slider {...settings}>
                {products.map((product, index) => (
                    <div key={product.id} className="featured-product">
                        <div
                            className="featured-product-inner"
                            style={{ background: bannerGradients[index % bannerGradients.length] }}
                        >
                            <img
                                src={product.imageURL}
                                alt={product.productName}
                                className="product-image-slider"
                            />
                            <h2 className="product-name-slider">{product.productName}</h2>
                            <button
                                className="shop-now-button"
                                onClick={() => handleButtonClick(product.id)}
                            >
                                Shop Product
                            </button>
                        </div>
                    </div>
                ))}
            </Slider>
        </div>
    );
};

export default FeaturedSlider;
