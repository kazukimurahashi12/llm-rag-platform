import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#7FFF00",
      light: "#B2FF66",
      dark: "#5FCC00",
    },
    secondary: {
      main: "#1f7a8c",
    },
    background: {
      default: "#ffffff",
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
          border: "1px solid rgba(127, 255, 0, 0.16)",
          boxShadow: "0 12px 32px rgba(0, 0, 0, 0.03)",
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
