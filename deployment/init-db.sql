-- Create databases if they don't exist
SELECT 'CREATE DATABASE user_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'user_db')\gexec

SELECT 'CREATE DATABASE incident_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'incident_db')\gexec

-- Grant all privileges
GRANT ALL PRIVILEGES ON DATABASE user_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE incident_db TO postgres;

