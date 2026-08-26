import React, { useState } from 'react';
import '../styles/AccountPage.css';
import { API_URL } from '../config';
import { useAuth } from '../context/AuthContext';

function AccountPage() {
    const { login } = useAuth();
    const [message, setMessage] = useState('');
    const [usernameReg, setUsernameReg] = useState('');
    const [emailReg, setEmailReg] = useState('');
    const [passwordReg, setPasswordReg] = useState('');
    const [emailLogin, setEmailLogin] = useState('');
    const [passwordLogin, setPasswordLogin] = useState('');
    const [isRegistering, setIsRegistering] = useState(false);
    const [isResettingPassword, setIsResettingPassword] = useState(false);
    const [resetEmail, setResetEmail] = useState('');

    // Register a new user handler
    const handleRegister = async () => {
        const response = await fetch(`${API_URL}/api/users/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ username: usernameReg, email: emailReg, password: passwordReg }),
        });

        const text = await response.text();
        if (response.status === 400) {
            window.alert(text);
        } else {
            setMessage(text);
        }
    };

    const handleLogin = async () => {
        try {
            const user = await login(emailLogin, passwordLogin);
            setMessage(`Welcome back, ${user.username}!`);
        } catch (err) {
            window.alert(err.message);
        }
    };

    // Password reset request - sends a reset link to the given email
    const handlePasswordReset = async () => {
        const response = await fetch(`${API_URL}/api/users/reset-password/request`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ email: resetEmail }),
        });

        const text = await response.text();
        if (!response.ok) {
            window.alert(text);
        } else {
            setMessage(text);
        }
    };

    // Toggle between login and register form
    const toggleForm = () => {
        setIsRegistering(!isRegistering);
        setMessage('');
    };

    // Toggle between login and password reset form
    const togglePasswordReset = () => {
        setIsResettingPassword(!isResettingPassword);
        setMessage('');
    };

    return (
        <div className="body">
            <img
                src={'https://i.postimg.cc/0QQ0czTg/Logo2-Crop.png'}
                alt="MyApp Logo"
                className="logo-img-login"
            />
            <div className="input-container">
                <h2>
                    {isResettingPassword
                        ? 'Reset Password'
                        : isRegistering
                          ? 'Create Account'
                          : 'Sign in'}
                </h2>
                {isResettingPassword ? (
                    <>
                        <div className="reset-container">
                            <label>Email:</label>
                            <input
                                type="text"
                                placeholder="john@example.com"
                                value={resetEmail}
                                onChange={(e) => setResetEmail(e.target.value)}
                            />
                        </div>
                        <button onClick={handlePasswordReset} className="submit-button">
                            Send Reset Link
                        </button>
                        <p>
                            Remembered your password?{' '}
                            <button
                                type="button"
                                className="link-button"
                                onClick={togglePasswordReset}
                            >
                                Sign in
                            </button>
                        </p>
                    </>
                ) : isRegistering ? (
                    <>
                        <div className="registration-container">
                            <label>Your name:</label>
                            <input
                                type="text"
                                placeholder="First and last name"
                                value={usernameReg}
                                onChange={(e) => setUsernameReg(e.target.value)}
                            />
                        </div>
                        <div className="registration-container">
                            <label>Email:</label>
                            <input
                                type="text"
                                placeholder="john@example.com"
                                value={emailReg}
                                onChange={(e) => setEmailReg(e.target.value)}
                            />
                        </div>
                        <div className="registration-container">
                            <label>Password:</label>
                            <input
                                type="password"
                                placeholder="password"
                                value={passwordReg}
                                onChange={(e) => setPasswordReg(e.target.value)}
                            />
                        </div>
                        <button onClick={handleRegister} className="submit-button">
                            Submit
                        </button>
                        <p>
                            Already have an account?{' '}
                            <button type="button" className="link-button" onClick={toggleForm}>
                                Sign in{' '}
                            </button>
                        </p>
                    </>
                ) : (
                    <>
                        <div className="login-container">
                            <label>Email:</label>
                            <input
                                type="text"
                                placeholder="john@example.com"
                                value={emailLogin}
                                onChange={(e) => setEmailLogin(e.target.value)}
                            />
                        </div>
                        <div className="login-container">
                            <label>Password:</label>
                            <input
                                type="password"
                                placeholder="password"
                                value={passwordLogin}
                                onChange={(e) => setPasswordLogin(e.target.value)}
                            />
                        </div>
                        <button onClick={handleLogin} className="submit-button">
                            Login
                        </button>
                        <p>New to the store? </p>
                        <button type="button" className="link-button" onClick={toggleForm}>
                            Create your Store Account
                        </button>
                        <p>
                            Forgot your password?{' '}
                            <button
                                type="button"
                                className="link-button"
                                onClick={togglePasswordReset}
                            >
                                Reset here
                            </button>
                        </p>
                    </>
                )}
                <p>{message}</p>
            </div>
        </div>
    );
}

export default AccountPage;
