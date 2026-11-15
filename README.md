# InventoryPro — Inventory Management System

A runnable starter application for a Nigerian retail shop. Built with Java 25, Spring Boot 4, Spring Data JPA, Spring Security, MySQL, HTML, CSS and JavaScript.

## Included features

- Admin sign-in (demo in-memory administrator)
- Dashboard with cash balance, product count, stock, sales, purchases and low-stock alerts
- Product create, read, update and delete
- Product search
- Record sales: stock goes down and cash goes up
- Record purchases: stock goes up and cash goes down
- Manual cash-in and cash-out
- Cash transaction ledger with balance-after values
- Sales and purchase history
- CSV exports for products and cash transactions
- Database transaction boundaries for sales/purchases/cash changes
- Insufficient stock and insufficient cash checks

## Requirements

- Java 25 (JDK 25)
- Maven 3.9+
- MySQL 8+

## Setup

1. Make sure MySQL is running. The database `inventory_db` is created automatically.
2. Set environment variables (or edit `application.properties`):
   ```bash
   export DB_PASSWORD='your-mysql-password'          # DB_USERNAME defaults to root
   export INVENTORY_ADMIN_PASSWORD='choose-a-strong-password'
   # optional: INVENTORY_ADMIN_USERNAME (default admin), INVENTORY_SEED_DEMO=true (loads 5 sample products)
   ```
   If `INVENTORY_ADMIN_PASSWORD` is not set, a random password is generated and printed in the console at start-up.
3. Run `mvn spring-boot:run` (or run `InventoryApplication` from IntelliJ IDEA).
4. Visit `http://localhost:8080` and sign in as `admin`.

Run the tests with `mvn test`.

## Sales cart, settings and reports

- **Cart sales:** on the Sales page, search a product by name or SKU, click it, check or change the selling price (it is pre-filled from the product), set the quantity and add it to the cart. Add as many products as you like, edit quantities and prices in the cart, then Complete Sale. One sale adds the whole cart total to Cash Balance and reduces every product's stock, all in one database transaction.
- **Receipts are optional:** tick "Print receipt after sale" (remembered on this browser), or press **Receipt** on any past sale. Receipts are laid out for 80 mm thermal printers and normal printers and show your business name, logo, address and phone.
- **Sales records:** Today, This week (Monday start), This month, This year, or Custom dates, with sales count, revenue and profit for the period.
- **Settings:** edit the business name, address, phone and logo. Under *Delete a sale* you can delete any sale: its products go back to stock and its amount is taken out of Cash Balance, with a `SALE DELETED` entry in the cash history. If Cash Balance is lower than the sale amount the deletion is refused until you add cash.
- **Reports:** Total products value (stock x cost price), Expected product value (stock x selling price), expected profit, profit made from sales for the chosen period, and a per-product table. **Export to Excel** downloads a Summary sheet and a Products sheet.
- Profit uses the cost price recorded **at the time of the sale**, so later price changes do not rewrite old profit.
- Sales recorded with the earlier single-product version of this app are not shown in the new sales records or reports (they stay in the database).

## Offline and online

The app works with no internet at all, and can also be hosted online with HTTPS. Start scripts, backup scripts, a Docker/Caddy setup and a step-by-step guide are in the `local/`, `deploy/` and `docs/OFFLINE-AND-ONLINE.md` files.

## Pagination

Products, sales records, purchase history, cash history, the delete-sale list and the report's product table show **25 rows per page** with Prev / page numbers / Next and a "Showing 26–50 of 1,000" line. Paging is done in the database, so large catalogues stay fast. The API takes `?page=0&size=25` (page is 0-based, size is capped at 100) and returns `{items, page, size, totalElements, totalPages}`. The sales search box queries the server as you type and never loads the whole catalogue.

## Users and roles (RBAC)

| Role | Can do |
|---|---|
| **Inventory Admin** | Everything: products, import, purchases, cash balance, reports, settings, users, deleting sales |
| **Accountant** | Only sell products (Sales page and cart) and view sales records, receipts included |

- The first Inventory Admin is created on first start from `INVENTORY_ADMIN_USERNAME` / `INVENTORY_ADMIN_PASSWORD` (a random password is printed in the console if you do not set one).
- Admins manage users under **Settings → Users & roles**: add users, change roles, enable/disable, reset passwords, delete. You cannot demote, disable or delete yourself, and there must always be one active admin.
- Access is enforced on the server for every request (not just by hiding menus), and a user's role or disabled status takes effect on their very next click.
- Forgot the admin password? Start once with `INVENTORY_ADMIN_PASSWORD=new-password` and `INVENTORY_RESET_ADMIN_PASSWORD=true`.

## Admin-only settings

- **Clear All Data** (Settings → Danger zone): erases all products, sales, purchases and the cash history and sets Cash Balance to zero. You must type `DELETE ALL DATA` and re-enter your password. User accounts and the business profile/settings are kept so you can still sign in.

## Cash balance rules

- **Adding a product** (form or Excel import) debits quantity × cost price from Cash Balance, recorded as `STOCK ADDED` in the cash ledger and as an "Opening stock" purchase. It is rejected if cash is insufficient. Untick "Debit cash balance" to load stock without touching cash.
- Editing a product is a correction and never moves cash; use Purchases to buy more stock.
- A sale adds the sale total to Cash Balance and reduces product quantity.
- A purchase deducts the purchase total from Cash Balance and increases product quantity.
- Manual cash-in adds money without changing stock.
- Manual cash-out subtracts money without changing stock.
- A transaction is saved together with its related stock/cash update.
- Purchase and cash-out operations are rejected if the available cash is insufficient.

## Important accounting note

The app treats the Cash Balance as money on hand, not the value of inventory. Sale and purchase amounts are based on the unit price entered on the transaction form. Product cost and selling prices are defaults for reference; the transaction form lets the operator enter the actual transaction price.

## API overview

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/dashboard` | Dashboard totals |
| GET | `/api/products?q=&page=&size=` | Paged product list (admin) |
| GET | `/api/products/search?q=&limit=` | Sales search box (admin, accountant) |
| GET/POST/PUT/DELETE | `/api/users` | Users and roles (admin) |
| POST | `/api/admin/clear-data` | Erase all business data (admin) |
| GET | `/api/me` | Signed-in user and role |
| POST | `/api/products?debitCash=true` | Create product (debits stock cost from cash by default) |
| POST | `/api/products/import?debitCash=true` | Import products from .xlsx |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product if no recent transaction references it |
| GET | `/api/cash` | Current cash balance |
| POST | `/api/cash/add` | Manual cash-in |
| POST | `/api/cash/withdraw` | Manual cash-out |
| GET | `/api/cash/transactions` | Cash ledger |
| POST | `/api/sales` | Record a cart sale (many products) |
| GET | `/api/sales?range=today\|week\|month\|year\|custom&from=&to=` | Sales records with revenue and profit |
| DELETE | `/api/sales/{id}` | Delete a sale and restore stock and cash |
| GET/PUT | `/api/settings` | Business name, address, phone, logo |
| GET | `/api/reports?range=` | Stock value, expected value, profit, product details |
| GET | `/api/reports/export?range=` | The same report as an Excel file |
| POST | `/api/purchases` | Record purchase |
| GET | `/api/purchases` | Purchase history |
| GET | `/api/transactions` | Combined business transaction history |

## Example request bodies

Create product:
```json
{
  "name": "Rice (5kg)",
  "sku": "RICE-005",
  "category": "Grains",
  "quantity": 20,
  "reorderLevel": 5,
  "costPrice": 4200,
  "sellingPrice": 5500
}
```

Sale or purchase:
```json
{
  "productId": 1,
  "quantity": 2,
  "unitPrice": 5500,
  "partyName": "Walk-in customer",
  "note": "Counter sale"
}
```

Manual cash-in / cash-out:
```json
{
  "amount": 25000,
  "description": "Additional shop capital"
}
```

## Scope notes

This is a complete local learning/MVP project for the features listed above, not a certified accounting package or a production-hardened POS. Supplier names are captured on purchase records rather than maintained as a separate supplier directory. Categories are managed as product category text. Sales currently record one product per transaction. Add persistent user management, supplier/category CRUD, multi-item invoices, receipt printing, automated backups, and comprehensive automated tests before deploying for a real business.


## Import products from Excel

Open **Products** and choose **Download Excel Template**. Fill in the template and save it as `.xlsx`, then choose **Import Excel** to upload it. Required columns are `Name`, `SKU`, `Category`, `Quantity`, `Cost Price`, and `Selling Price`; `Reorder Level` is optional and defaults to 5. Prices should be plain numbers (for example, `1500.00`), without the naira symbol. Existing SKUs and invalid rows are skipped and listed in the import report. By default each imported row debits quantity × cost price from Cash Balance; rows are skipped (and reported) if cash runs out. Untick the debit option to load the catalogue without changing cash. At most 5,000 rows and 5 MB per file.
