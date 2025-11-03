# 🏭 Concurrency-Heavy Inventory System

A practical **warehousing API** built with **Spring Boot + JPA** to demonstrate **concurrency handling**, stock management, and warehouse operations.  
This project is designed for real-world scenarios where multiple requests update stock simultaneously (e.g., e-commerce, logistics).

---

## 📌 Features
- **Product & Warehouse Management**
    - Add products and warehouses
    - Link products to warehouses with stock entries

- **Inventory Management**
    - Adjust stock (add/remove quantities)
    - Reserve stock for orders
    - Release/cancel reservations

- **Concurrency Handling**
    - **Optimistic Locking** via `@Version` field
    - Automatic **retry mechanism** for failed concurrent updates
    - Database constraints for unique `(product_id, warehouse_id)`

- **Error Handling**
    - Clear error messages (`404 Product not found`, `404 Warehouse not found`, `409 Insufficient stock`, etc.)
    - Global exception handler with meaningful HTTP responses

---

## ⚙️ Tech Stack
- **Java 24**
- **Spring Boot 3.5.4**
- **Spring Data JPA (Hibernate)**
- **Postgres Database**
- **JWT Auth For Security**
- **Maven**

---
