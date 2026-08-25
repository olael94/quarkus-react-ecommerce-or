import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar/Navbar';
import { AuthProvider } from './context/AuthContext';
import HomePage from './pages/HomePage'; // Ensure you have a HomePage component
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import AccountPage from './pages/AccountPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import CartPage from './pages/CartPage';
import { CartProvider } from './context/CartContext';
import CartDrawer from './components/CartDrawer/CartDrawer';

const App = () => {
    return (
        //BrowserRouter allows navigating between pages
        <BrowserRouter>
            <AuthProvider>
                <CartProvider>
                    <Navbar />
                    <CartDrawer />
                    <Routes>
                        <Route path="/" element={<HomePage />} />
                        <Route path="/products" element={<ProductsPage />} />
                        <Route path="/products/:id" element={<ProductDetailPage />} />
                        <Route path="/account" element={<AccountPage />} />
                        <Route path="/reset-password" element={<ResetPasswordPage />} />
                        <Route path="/cart" element={<CartPage />} />
                    </Routes>
                </CartProvider>
            </AuthProvider>
        </BrowserRouter>
    );
};

export default App;
