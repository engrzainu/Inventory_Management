# Running the system offline and online

You can use the same program in two ways. They are **two separate copies with separate data**:

| | **Offline (shop computer)** | **Online (internet server)** |
|---|---|---|
| Needs internet | No | Yes |
| Open from | The shop computer, and phones/laptops on the same Wi-Fi | Any phone or computer, anywhere |
| Data stored | On the shop computer | On the server |
| Cost | Free | A small monthly server fee, plus a domain name |

The program uses no outside files or fonts, so it works with the internet switched off.

---

## A. Offline: run it on the shop computer (Windows)

**Install once**
1. **Java 25 (JDK 25)** from adoptium.net.
2. **MySQL 8** (MySQL Installer for Windows). Remember the root password you set. Leave "Start MySQL as a Windows service" ticked so it starts with the computer.
3. **Maven**, only to build the program the first time (or ask whoever set this up to give you the finished `.jar` file).

**Build once** (open a terminal in the project folder):
```
mvn clean package -DskipTests
```
This creates `target\inventory-management-2.0.0.jar`.

**Start**
1. Open the `local` folder and double-click **`start-inventory.bat`**.
2. The first time it creates `inventory.env` and opens it. Set your MySQL password and an admin password, save, and run `start-inventory.bat` again.
3. Your browser opens at **http://localhost:8080**. Sign in as the admin.
4. Keep the black window open while the shop is working. Closing it stops the system (your data stays safe in MySQL).

**Use it from phones and other computers in the shop (same Wi-Fi, no internet needed)**
- The start window prints the computer's address, for example `192.168.1.20`. On another device open `http://192.168.1.20:8080`.
- If it does not open, Windows Firewall is blocking it: allow inbound TCP port **8080** (Windows Security → Firewall → Advanced settings → Inbound Rules → New Rule → Port 8080).
- Ask your router to give the shop computer a fixed address so it does not change.
- Create an **Accountant** user (Settings → Users & roles) for staff who only sell.

**Start automatically when the computer turns on:** press `Win + R`, type `shell:startup`, and put a shortcut to `start-inventory.bat` in that folder.

**Backups (important):** double-click **`local\backup-database.bat`** every day. It saves a `.sql` file in `local\backups`. Copy the file to a flash drive or cloud storage as well. To restore, stop the program and run `restore-database.bat backups\your-file.sql`.

---

## B. Online: put it on the internet (HTTPS)

You need:
- A small **Linux server (VPS)**, Ubuntu 22.04 or 24.04, 2 GB RAM or more.
- A **domain name** (for example `shop.yourname.com`) with an **A record** pointing to the server's IP address. Free HTTPS certificates need a domain.
- **Docker** installed on the server (`curl -fsSL https://get.docker.com | sh`).

**Steps**
1. On your computer build the program: `mvn clean package -DskipTests`.
2. Copy the whole project folder (including `target/`) to the server, for example into `/opt/inventory`.
3. On the server:
   ```
   cd /opt/inventory/deploy
   cp .env.example .env
   nano .env          # set DOMAIN, DB_PASSWORD and INVENTORY_ADMIN_PASSWORD, then save
   docker compose up -d --build
   ```
4. Wait a minute, then open **https://your-domain**. Caddy gets the HTTPS certificate by itself. Sign in as the admin.
5. Check progress or errors with `docker compose logs -f app`.

**Firewall:** allow only ports **22 (SSH), 80 and 443**. The database and the app are not published to the internet, only the HTTPS front door (Caddy) is.

**Update to a new version:** build a new `.jar`, copy it to the server's `target/` folder, then `docker compose up -d --build`. Your data is kept in a Docker volume.

**Backups (important):** run `./backup.sh` in the `deploy` folder. Schedule it daily with `crontab -e`:
```
0 2 * * * cd /opt/inventory/deploy && ./backup.sh
```
It keeps the newest 30 backups in `deploy/backups`. Download copies to your own computer regularly, because a backup on the same server does not protect you if the server is lost.
Restore: `gunzip -c backups/FILE.sql.gz | docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" inventory_db'`

**Safety built in:** HTTPS only, secure cookies, sign-in is blocked for 10 minutes after 5 wrong passwords, users are signed out after 8 idle hours, and every request is checked against the user's role. Still: use long unique passwords, create separate users for staff, and never share the `.env` file.

---

## C. Moving data between the offline copy and the online copy

The two copies do **not** sync automatically. To copy the shop computer's data to the server once:
1. On the shop computer run `local\backup-database.bat`.
2. Copy the `.sql` file to the server and restore it with the command in section B (use `cat file.sql | docker compose exec -T db sh -c '...'` for an uncompressed file).

Decide which copy is the "real" one for daily selling. If you sell in both places, the stock and cash balances will differ and must be corrected by hand.
If you need one shared system that keeps working when the internet drops and syncs later, that is a bigger feature (offline sales storage and syncing). Ask and I can build it.

## How offline selling works (Sales page)

- The **online server is the single source of truth**. Only the Sales page works offline.
- After you sign in with internet, the product list (names, SKUs, selling prices, stock, never cost prices) is saved on the device and refreshed every 5 minutes.
- With no internet, search products and complete sales as usual. Each sale is saved on the device (receipt number `OFF-0001`...) and shown in **Offline sales waiting to upload**. You can still print a provisional receipt.
- When the internet returns, sales upload automatically, oldest first, with their real sale time. Each carries a unique id, so a retry can never record a sale twice.
- If the server refuses a sale (for example the stock was sold elsewhere meanwhile) it stays in the list marked **Not accepted** with the reason. Fix the stock, then press **Retry**, or **Discard**.
- Sales older than 30 days cannot be uploaded. Sales records, reports and settings need internet.
- **HTTPS requirement:** browsers only allow the offline page cache on `https://` or `http://localhost`. On a plain `http://192.168.x.x` address, selling still works while the page stays open, but the page cannot be reopened offline. Use the Docker/Caddy HTTPS setup, or open the app via `localhost` on the shop PC.
- Sign in once with internet on each device before relying on offline mode. Signing out keeps unsent sales on the device.
