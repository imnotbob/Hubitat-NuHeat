# Hubitat-NuHeat


# nuHeat

Hubitat integration for NuHeat Signature floor-heating thermostats, using the NuHeat OAuth2 API (<https://api.mynuheat.com>).

NuHeat no longer accepts the email/password login used by older versions of this driver. This version is made up of:

- **NuHeat Connect** (app, `nuheat-connect.groovy`): logs in to NuHeat with OAuth2, keeps the tokens fresh, polls your thermostats, and creates one device per thermostat.
- **NuHeat Signature Thermostat** (driver, `nuheat-thermostat.groovy`): the thermostat device. It is created and updated by the app and cannot be used on its own.

## Before you start: get NuHeat API credentials

NuHeat does not offer self-service API keys; API access is only given to registered developers. To request it:

1. Go to <https://go.chemelex.com/connected-home> (NuHeat's connected-home page, hosted by Chemelex).
2. Fill in the API access request form at the bottom of the page.
3. For the redirect URI, enter exactly:
   `https://cloud.hubitat.com/oauth/stateredirect`

NuHeat will give you a **Client ID** and a **Client Secret**.

You don't need to choose a grant type or scopes; the form doesn't ask for them. When you log in, the app requests the scopes `openapi openid offline_access`. `offline_access` is what lets Hubitat stay logged in. If login fails with a scope error, or the app shows `disconnected` about an hour after every login, ask NuHeat to enable `offline_access` (refresh tokens) for your client.

## Install

### With Hubitat Package Manager (HPM)

1. In HPM, choose **Install** and search for **NuHeat Connect**, or install from this URL:
   `https://raw.githubusercontent.com/imnotbob/nuHeat/master/HE/packageManager.json`
2. HPM installs the app and driver and enables OAuth for the app.

### Manually

1. **Drivers Code → New Driver**, paste `nuheat-thermostat.groovy`, **Save**.
2. **Apps Code → New App**, paste `nuheat-connect.groovy`, **Save**.
3. On the app code page, click **OAuth → Enable OAuth in App → Update**. (The app tries to do this itself, but enabling it here avoids problems.)

## Set up

1. **Apps → Add User App → NuHeat Connect.**
   If you see a red message saying the driver is not installed, install the driver first and re-open the app.
2. Click **NuHeat API Authorization**, enter your **Client ID** and **Client Secret**.
3. Click **Log in to NuHeat**. A browser window opens; log in with your NuHeat (mynuheat.com) account and allow access.
   When you see "Your NuHeat account is now connected", close that window.
4. Back in Hubitat, click **Next**. The main page should now say **Connected**.
5. Click **Select Thermostats** and choose the thermostats to add.
6. Optional settings:
   - **Polling interval**: how often thermostat status is fetched (default 5 minutes).
   - **Notify these devices if the NuHeat connection is lost**: e.g. your phone, to get a message if you need to log in again.
7. Click **Done**. A device named `NuHeat <thermostat name>` is created for each selected thermostat.

## Using the thermostat device

| Command | What it does |
|---|---|
| `setHeatingSetpoint(temp)` | Holds `temp` for the driver's **Default Hold Time** (1–23 hours), then returns to the schedule |
| `setHeatingSetpointWithHoldHours(temp, hours)` | Holds `temp` for 1–23 hours |
| `setHeatingSetpointWithHoldTime(temp, date)` | Holds `temp` until `date` (must be within the next 23 hours) |
| `indefiniteHold(temp)` | Holds `temp` until you resume (uses **Default Indefinite Hold Temperature** if `temp` is empty) |
| `resume` | Returns to the thermostat's schedule |
| `setAwayMode(away\|home)` | Turns NuHeat away mode on or off. Away mode belongs to the NuHeat group, so it changes every thermostat in the same group |
| `off` | Indefinite hold at **Default Indefinite Hold Temperature** |
| `refresh` | Fetches current status (ignored if the last update was under a minute ago) |

Attributes include `temperature`, `heatingSetpoint`, `thermostatOperatingState` (`heating`/`idle`), `scheduleStatus` (`running`, `temporary hold`, `indefinite hold`), `holdUntil`, `home` (`home`/`away`), `online`, and `apiStatus` (`connected`, `warning`, `disconnected`).

Cooling and other thermostat modes are not supported; `coolingSetpoint` exists only so Google Home integration works.

## Connection status

The app renews its NuHeat token automatically. If renewing fails, it retries with increasing delays. If it keeps failing:

- `apiStatus` on each device changes to `warning`, then `disconnected`.
- The app name shows `(disconnected)` in red and polling stops.
- Selected notification devices get a message, repeated every 3 hours.

To reconnect, open **NuHeat Connect → NuHeat API Authorization → Re-authorize NuHeat account** and log in again. This is also needed if the hub is off for more than about 15 days, because the NuHeat refresh token expires.

## Upgrading from the old (username/password) driver

The old driver logged in with your NuHeat email and password and is no longer able to connect. Devices using it can't be converted in place:

1. Install and set up NuHeat Connect as above; it creates new thermostat devices.
2. Update your automations (rules, webCoRE), dashboards and integrations to use the new devices.
3. Delete the old devices.

## Troubleshooting

| Problem | Fix |
|---|---|
| "OAuth Initialization Failure" | Enable OAuth for NuHeat Connect in **Apps Code** (see Manual install, step 3). |
| NuHeat login page shows an error about the redirect URI or client | The Client ID, Secret or redirect URI registered with NuHeat doesn't match. The redirect URI must be exactly `https://cloud.hubitat.com/oauth/stateredirect`. |
| "The connection to NuHeat could not be established" after logging in | Check **Logs** for `NuHeatConnect -- Token request ... failed`. Usually a wrong Client Secret. |
| No thermostats listed | Make sure the thermostats are on the same NuHeat account you logged in with, and check the logs. |
| Setpoint changes don't stick | The driver logs `NuHeat did not accept ...`. Hold times must be within the next 23 hours. |

For more detail, set **Log Level** to `DEBUG` in the app and enable debug logging on the device.







## OLD Nuheat driver - THIS DOES NOT WORK ANYMORE

 Note: You will need an account which can only be setup on a thermostat and nuheat web, mobile app
 
This uses async I/O for the most common operations

To use this you will need your Nuheat username and password along with your thermostat(s) id. You can find the thermostat ID at online 1. Go into the thermostat and it is the "Thermostat ID".
 
Manufacturer Website: http://www.mynuheat.com
 *  Product Page: http://www.nuheat.com/products/thermostats/signature
