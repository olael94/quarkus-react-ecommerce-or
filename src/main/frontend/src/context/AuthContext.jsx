import React, { createContext, useContext, useState, useEffect } from 'react';
import { API_URL } from '../config';

const AuthContext = createContext();

// Helper function to get CSRF token from cookies
export function getCsrfToken() {
    const match = document.cookie.match(/(?:^|; )csrf_token=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

// This is the AuthProvider component that wraps the entire app
export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        // Fetch user data from the server
        fetch(`${API_URL}/api/users/me`, { credentials: 'include' })
            .then((res) => (res.ok ? res.json() : null))
            .then((data) => setUser(data))
            .finally(() => setLoading(false));
    }, []);

    // Login function
    const login = async (email, password) => {
        const response = await fetch(`${API_URL}/api/users/login`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });

        if (!response.ok) {
            throw new Error(await response.text());
        }

        const data = await response.json();
        setUser(data);
        return data;
    };

    const logout = async () => {
        await fetch(`${API_URL}/api/users/logout`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'X-CSRF-Token': getCsrfToken() },
        });
        setUser(null);
    };

    return (
        <AuthContext.Provider value={{ user, loading, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    return useContext(AuthContext);
}
