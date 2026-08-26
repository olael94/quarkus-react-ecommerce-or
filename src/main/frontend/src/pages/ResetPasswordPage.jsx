import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import '../styles/AccountPage.css';
import { API_URL } from '../config';

function ResetPasswordPage() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();

    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [message, setMessage] = useState('');
    // 'checking' | 'valid' | 'invalid' - whether this link can still be used,
    // checked up front so the user isn't told it's dead only after filling
    // out and submitting the form.
    const [tokenStatus, setTokenStatus] = useState('checking');

    useEffect(() => {
        if (!token) {
            return;
        }
        fetch(`${API_URL}/api/users/reset-password/validate?token=${encodeURIComponent(token)}`)
            .then((res) => setTokenStatus(res.ok ? 'valid' : 'invalid'))
            .catch(() => setTokenStatus('invalid'));
    }, [token]);

    const handleSubmit = async () => {
        if (newPassword !== confirmPassword) {
            window.alert('Passwords do not match');
            return;
        }

        const response = await fetch(`${API_URL}/api/users/reset-password/confirm`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token, newPassword }),
        });

        const text = await response.text();
        if (!response.ok) {
            window.alert(text);
            return;
        }

        setMessage(text);
        setTimeout(() => navigate('/account'), 2000);
    };

    if (!token) {
        return (
            <div className="body reset-password-body">
                <div className="input-container">
                    <h2>Invalid reset link</h2>
                    <p>This password reset link is missing its token.</p>
                </div>
            </div>
        );
    }

    if (tokenStatus === 'checking') {
        return (
            <div className="body reset-password-body">
                <div className="input-container">
                    <h2>Checking link...</h2>
                </div>
            </div>
        );
    }

    if (tokenStatus === 'invalid') {
        return (
            <div className="body reset-password-body">
                <div className="input-container">
                    <h2>Invalid reset link</h2>
                    <p>This link is invalid or has expired.</p>
                    <p>
                        <Link to="/account">Request a new one</Link>
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className="body reset-password-body">
            <div className="input-container">
                <h2>Set a new password</h2>
                {message ? (
                    <p>{message}</p>
                ) : (
                    <>
                        <div className="reset-container">
                            <label>New Password:</label>
                            <input
                                type="password"
                                placeholder="New password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                            />
                        </div>
                        <div className="reset-container">
                            <label>Confirm Password:</label>
                            <input
                                type="password"
                                placeholder="Confirm password"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                            />
                        </div>
                        <button onClick={handleSubmit} className="submit-button">
                            Reset Password
                        </button>
                    </>
                )}
            </div>
        </div>
    );
}

export default ResetPasswordPage;
