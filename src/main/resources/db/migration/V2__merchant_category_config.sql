CREATE TABLE merchant_category_config (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(100) NOT NULL UNIQUE,
    action VARCHAR(20) NOT NULL
);

INSERT INTO merchant_category_config (category, action) VALUES
    ('darkweb', 'DECLINE'),
    ('illegal', 'DECLINE'),
    ('gambling', 'REVIEW'),
    ('crypto', 'REVIEW'),
    ('adult', 'REVIEW'),
    ('firearms', 'REVIEW'),
    ('wire_transfer', 'REVIEW');
