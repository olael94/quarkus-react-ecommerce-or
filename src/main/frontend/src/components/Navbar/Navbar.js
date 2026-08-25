import React, { useState, useRef, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { HiOutlineUser, HiOutlineShoppingBag } from 'react-icons/hi2';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../context/CartContext';
import './Navbar.css';

const Navbar = () => {
    const { user, logout } = useAuth();
    const { itemCount } = useCart();
    // State variable to keep track of whether the mobile menu is open or closed
    const [isOpen, setIsOpen] = useState(false);
    // Function to toggle the mobile menu
    const toggleMenu = () => {
        setIsOpen(!isOpen);
    };

    // State + ref for the account avatar dropdown
    const [accountMenuOpen, setAccountMenuOpen] = useState(false);
    const accountMenuRef = useRef(null);

    // State for the guest (logged-out) "Sign in" hover dropdown
    const [guestMenuOpen, setGuestMenuOpen] = useState(false);

    // Close the account dropdown when clicking anywhere outside it
    useEffect(() => {
        function handleClickOutside(event) {
            if (accountMenuRef.current && !accountMenuRef.current.contains(event.target)) {
                setAccountMenuOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleLogout = () => {
        setAccountMenuOpen(false);
        logout();
    };

    return (
        <nav>
            <div className="nav-inner">
                <div className="logo">
                    <NavLink to="/">
                        <img
                            src={'https://i.postimg.cc/0QQ0czTg/Logo2-Crop.png'}
                            alt="MyApp Logo"
                            className="logo-img"
                        />
                    </NavLink>
                </div>
                <div className={`menu ${isOpen ? 'open' : ''}`}>
                    <ul className="nav-primary">
                        <li>
                            <NavLink
                                to="/products"
                                className={({ isActive }) => (isActive ? 'active' : undefined)}
                            >
                                Products
                            </NavLink>
                        </li>
                    </ul>
                    <ul className="nav-secondary">
                        <li>
                            <NavLink
                                to="/cart"
                                aria-label="Cart"
                                className={({ isActive }) => (isActive ? 'active' : undefined)}
                            >
                                <span className="cart-icon-wrapper">
                                    <HiOutlineShoppingBag size={22} />
                                    {itemCount > 0 && (
                                        <span className="cart-badge">{itemCount}</span>
                                    )}
                                </span>
                            </NavLink>
                        </li>
                        <li>
                            {user ? (
                                <div className="account-menu" ref={accountMenuRef}>
                                    <button
                                        type="button"
                                        className="avatar-btn"
                                        onClick={() => setAccountMenuOpen((open) => !open)}
                                        aria-haspopup="true"
                                        aria-expanded={accountMenuOpen}
                                    >
                                        {user.username.charAt(0).toUpperCase()}
                                    </button>
                                    {accountMenuOpen && (
                                        <div className="account-dropdown">
                                            <div className="account-dropdown-panel">
                                                <div className="account-dropdown-header">
                                                    Signed in as <strong>{user.username}</strong>
                                                </div>
                                                <button type="button" onClick={handleLogout}>
                                                    Log out
                                                </button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <div
                                    className="account-menu"
                                    onMouseEnter={() => setGuestMenuOpen(true)}
                                    onMouseLeave={() => setGuestMenuOpen(false)}
                                >
                                    <NavLink
                                        to="/account"
                                        className={({ isActive }) =>
                                            isActive ? 'signin-trigger active' : 'signin-trigger'
                                        }
                                    >
                                        <HiOutlineUser size={19} />
                                        Sign in
                                    </NavLink>
                                    {guestMenuOpen && (
                                        <div className="account-dropdown">
                                            <div className="account-dropdown-panel">
                                                <NavLink
                                                    to="/account"
                                                    className="dropdown-signin-btn"
                                                >
                                                    Sign in
                                                </NavLink>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </li>
                    </ul>
                </div>
                {/* Mobile menu icon state for Hamburger animation */}
                <div className={`mobileMenu ${isOpen ? 'open' : ''}`} onClick={toggleMenu}>
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            </div>
        </nav>
    );
};

export default Navbar;
