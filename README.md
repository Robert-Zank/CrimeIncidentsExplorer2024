# Crime Incidents Explorer

A Java Swing desktop application that demonstrates advanced SQL usage and a 3NF database design for analyzing crime incident data. This project showcases:

- **SQL Normalization & 3NF**: Raw data is staged in MySQL, then transformed into dimension tables (`dim_shift`, `dim_method`, `dim_offense`, `dim_block`) and a central fact table (`fact_incident`).
- **Dynamic Filtering**: Users can filter incidents by date range, shift, method, offense, and block through a responsive UI.
- **Top‑N Queries**: Built‑in reporting menu to display the top N city blocks by incident count, using SQL window functions and ranking.
- **JDBC & PreparedStatement**: Secure, parameterized queries prevent SQL injection and handle dynamic search criteria.
- **Modular Code Structure**: Clear separation between UI (`MainFrame`, `FilterPanel`, `ResultsPanel`), data access (`DBConnector`, `DBUtil`), and domain logic (`FilterCriteria`).

## Features

- Load lookup values from dimension tables into dropdowns at startup.
- Build and execute dynamic SQL based on user selections.
- Display results in a sortable, non‑editable `JTable` with a live status bar.
- Menu‑driven reports leveraging MySQL window functions (e.g., `RANK() OVER`).

