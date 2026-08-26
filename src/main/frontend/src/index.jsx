import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App'; // Only import App here
import reportWebVitals from './reportWebVitals';

// Where React application will be rendered
const root = ReactDOM.createRoot(document.getElementById('root'));

// Where React starts rendering the application
root.render(
    // Wrapper component that runs checks to ensure that the application is working correctly
    <React.StrictMode>
        <App /> {/* Just render the App component */}
    </React.StrictMode>
);

// Performance reporting
reportWebVitals();
