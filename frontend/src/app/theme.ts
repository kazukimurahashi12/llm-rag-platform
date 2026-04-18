import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#41a862",
      light: "#77ca90",
      dark: "#2b7a46",
    },
    secondary: {
      main: "#1f7a8c",
    },
    background: {
      default: "#eff6f0",
      paper: "#ffffff",
    },
    success: {
      main: "#2f8f54",
    },
    warning: {
      main: "#d89a27",
    },
    error: {
      main: "#d05b4d",
    },
    info: {
      main: "#2f7ee6",
    },
  },
  shape: {
    borderRadius: 18,
  },
  typography: {
    fontFamily: '"Segoe UI", "Helvetica Neue", sans-serif',
    h3: {
      fontWeight: 700,
    },
    h4: {
      fontWeight: 700,
    },
    h5: {
      fontWeight: 700,
    },
    h6: {
      fontWeight: 700,
    },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          border: "1px solid rgba(66, 128, 86, 0.12)",
          boxShadow: "0 18px 48px rgba(18, 45, 28, 0.05)",
          backgroundImage: "none",
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 24,
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 999,
          textTransform: "none",
          fontWeight: 700,
        },
      },
    },
  },
});
