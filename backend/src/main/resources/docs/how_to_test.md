## How to test?

### Open API (Swagger) UI

For Open API (Swagger) Documentation UI and the endpoints, open http://localhost:8080/swagger-ui/index.html after running the app.

<br/>

### Postman Collection

The [Postman Collection](postman/e-wallet.postman_collection.json) shared in the resources can be modified and used
for testing the endpoints.

<br/>

### API Endpoints

> [!NOTE]
> All URIs are relative to *http://localhost:8080/api/v1*

<br/>

| Class              | Method                                                                     | HTTP request                                | Description                                             |
|--------------------|----------------------------------------------------------------------------|---------------------------------------------|---------------------------------------------------------|
| *AuthController*   | [**login**](http://localhost:8080/api/v1/auth/login)                       | **POST** /auth/login                        | Authenticates users by their credentials                |
| *AuthController*   | [**signup**](http://localhost:8080/api/v1/auth/signup)                     | **POST** /auth/signup                       | Registers users using their credentials and user info   |

<br/>
<br/>

| Class              | Method                                                                     | HTTP request                               | Description                                             |
|--------------------|----------------------------------------------------------------------------|--------------------------------------------|---------------------------------------------------------|
| *WalletController* | [**findById**](http://localhost:8080/api/v1/wallets/{id})                  | **GET** /wallets/{id}                      | Retrieves a single wallet by the given id               |
| *WalletController* | [**findByIban**](http://localhost:8080/api/v1/wallets/iban/{iban})         | **GET** /wallets/iban/{iban}               | Retrieves a single wallet by the given iban             |
| *WalletController* | [**findAllByUserId**](http://localhost:8080/api/v1/wallets/users/{userId}) | **GET** /wallets/users/{userId}            | Retrieves all wallets based on the given userId         |
| *WalletController* | [**findAll**](http://localhost:8080/api/v1/wallets)                        | **GET** /wallets?page=0&size=10&sort=id,asc | Retrieves all wallets based on the given parameters     |

<br/>
<br/>

| Class                   | Method                                                                                              | HTTP request                                       | Description                                                  |
|-------------------------|-----------------------------------------------------------------------------------------------------|----------------------------------------------------|--------------------------------------------------------------|
| *TransactionController* | [**findById**](http://localhost:8080/api/v1/transactions/{id})                                      | **GET** /transactions/{id}                         | Retrieves a single transaction by the given id               |
| *TransactionController* | [**findByReferenceNumber**](http://localhost:8080/api/v1/transactions/references/{referenceNumber}) | **GET** /transactions/references/{referenceNumber} | Retrieves a single transaction by the given reference number |
| *TransactionController* | [**findAllByUserId**](http://localhost:8080/api/v1/transactions/users/{userId})                     | **GET** /transactions/users/{userId}               | Retrieves all transactions based on the given userId         |
| *TransactionController* | [**findAll**](http://localhost:8080/api/v1/transactions)                                            | **GET** /transactions?page=0&size=10&sort=id,asc   | Retrieves all transactions based on the given parameters     |

<br/>
<br/>

### Unit & Integration Tests
Unit Tests for the services are provided in the corresponding packages.

<br/>
<br/>
