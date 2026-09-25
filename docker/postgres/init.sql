-- Credenciales exclusivas del entorno local, alineadas con application.yml.
CREATE USER orderflow WITH PASSWORD 'orderflow';
CREATE USER inventory WITH PASSWORD 'inventory';
CREATE USER payment WITH PASSWORD 'payment';
CREATE USER notification WITH PASSWORD 'notification';

CREATE DATABASE orderflow_orders OWNER orderflow;
CREATE DATABASE inventory OWNER inventory;
CREATE DATABASE payment OWNER payment;
CREATE DATABASE notification OWNER notification;

-- Cada usuario de aplicación solo puede conectarse a su propia base de datos.
REVOKE ALL ON DATABASE orderflow_orders FROM PUBLIC;
REVOKE ALL ON DATABASE inventory FROM PUBLIC;
REVOKE ALL ON DATABASE payment FROM PUBLIC;
REVOKE ALL ON DATABASE notification FROM PUBLIC;
