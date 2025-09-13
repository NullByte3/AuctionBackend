CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    auth_token VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT current_timestamp
);

CREATE TABLE items (
    id SERIAL PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    item_image TEXT,
    item_price DECIMAL(10, 2) NOT NULL,
    item_description TEXT,
    bid_increment DECIMAL(10, 2) NOT NULL,
    seller_id INTEGER REFERENCES users(id),
    winner_id INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT current_timestamp,
    end_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE bids (
    id SERIAL PRIMARY KEY,
    item_id INTEGER REFERENCES items(id),
    user_id INTEGER REFERENCES users(id),
    price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT current_timestamp
);